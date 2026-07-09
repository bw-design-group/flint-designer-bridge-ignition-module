"""
Flint Debugger - Python debugger using bdb.Bdb for VS Code DAP integration.

This module provides a debugger that:
- Supports breakpoints with optional conditions
- Provides stepping (over, into, out)
- Captures stack frames and variable information
- Communicates with VS Code via the DebugSession Java class
"""

import bdb
import sys
import traceback


class FlintDebugger(bdb.Bdb):
    """
    A Python debugger that integrates with the Flint debug session.
    Extends bdb.Bdb to provide breakpoint and stepping support.
    """

    def __init__(self, session, skip=None):
        """
        Initialize the debugger.

        Args:
            session: The Java DebugSession object for communication
            skip: Module patterns to skip (passed to bdb.Bdb)
        """
        bdb.Bdb.__init__(self, skip=skip)
        self.session = session
        self.current_frame = None
        self.stop_reason = None
        self._paused = False
        self._terminate = False
        self._continue_mode = False  # True when running to next breakpoint only
        self._shutting_down = False  # True when disable_tracing is called

        # Map Python frame to frame IDs
        self.frame_id_counter = 0
        self.frame_to_id = {}
        self.id_to_frame = {}

    def reset(self):
        """Reset the debugger state."""
        bdb.Bdb.reset(self)
        self.current_frame = None
        self.stop_reason = None
        self._paused = False
        self._terminate = False
        self._continue_mode = False  # True when running to next breakpoint only
        self._shutting_down = False  # True when disable_tracing is called
        self.frame_id_counter = 0
        self.frame_to_id = {}
        self.id_to_frame = {}

    def trace_dispatch(self, frame, event, arg):
        """Override to handle shutdown."""
        # Early exit if shutting down - prevents tracing into disable_tracing itself
        if self._shutting_down:
            return None

        return bdb.Bdb.trace_dispatch(self, frame, event, arg)

    def dispatch_line(self, frame):
        """Override dispatch_line to handle quitting properly."""
        if self.quitting:
            raise bdb.BdbQuit
        self.user_line(frame)
        if self.quitting:
            raise bdb.BdbQuit
        return self.trace_dispatch

    def break_anywhere(self, frame):
        """
        Override to always return True so we trace all frames.

        The default bdb.Bdb.break_anywhere checks self.breaks, but we use
        our own breakpoint system via session.getBreakpoints().

        By returning True, dispatch_call will return the trace function
        and continue tracing into function calls. We'll check for actual
        breakpoints in user_line.
        """
        # Always trace into functions - we check breakpoints in user_line
        return True

    def stop_here(self, frame):
        """
        Override bdb.Bdb.stop_here to properly handle continue mode.

        In bdb:
        - stopframe = None means "stop at every line" (set by set_step())
        - stopframe = specific frame means "stop when returning to that frame" (set_next/set_return)

        We need to respect _continue_mode to only stop at breakpoints when continuing.
        """
        if self._continue_mode:
            # In continue mode, don't stop for stepping - only breakpoints
            return False

        # When stopframe is None, bdb means "stop at every line" (step into mode)
        if self.stopframe is None:
            return True

        # Use parent's stop_here for step over/step out
        return bdb.Bdb.stop_here(self, frame)

    def user_line(self, frame):
        """
        Called when execution reaches a new line.
        This is where we check for breakpoints and handle stepping.
        """
        if self._terminate:
            raise SystemExit("Debug session terminated")

        # Get file and line info
        filename = self.canonic(frame.f_code.co_filename)
        lineno = frame.f_lineno

        # Skip internal frames (wrapper code, debugger internals)
        # This prevents stepping into stdout/stderr capture code
        if self._is_internal_frame(filename, frame):
            return

        # Check if we should stop here
        should_stop = False
        reason = None

        # Check for breakpoint first (always checked regardless of continue mode)
        if self._should_stop_at_breakpoint(filename, lineno, frame):
            should_stop = True
            reason = "breakpoint"

        # Check if stepping should stop here (only when not in continue mode)
        if not should_stop and self.stop_here(frame):
            should_stop = True
            reason = "step"

        if should_stop:
            self._handle_stop(frame, reason)

    def user_return(self, frame, return_value):
        """Called when a function returns."""
        if self._terminate:
            raise SystemExit("Debug session terminated")

        # Skip internal frames (wrapper code, debugger internals)
        filename = self.canonic(frame.f_code.co_filename)
        if self._is_internal_frame(filename, frame):
            return

        # In continue mode, don't stop on returns (only breakpoints)
        if self._continue_mode:
            return

        # If stepping out, stop at the return
        if self.stopframe is frame and self.returnframe is not frame:
            self._handle_stop(frame, "step")

    def user_exception(self, frame, exc_info):
        """Called when an exception occurs."""
        if self._terminate:
            raise SystemExit("Debug session terminated")

        exc_type, exc_value, exc_traceback = exc_info

        # Only stop on uncaught exceptions or if configured
        # For now, we'll just continue - can be enhanced later
        pass

    def _should_stop_at_breakpoint(self, filename, lineno, frame):
        """Check if we should stop at a breakpoint."""
        # Try multiple matching strategies for breakpoint lookup
        bp_info = self._find_breakpoint(filename, lineno)

        if bp_info is not None:
            # Check condition if any
            condition = bp_info.getCondition()
            if condition:
                try:
                    result = eval(condition, frame.f_globals, frame.f_locals)
                    if not result:
                        return False
                except:
                    # If condition evaluation fails, skip this breakpoint
                    return False

            # Check hit count if any
            hit_count = bp_info.getHitCount()
            if hit_count is not None:
                # TODO: Track hit counts per breakpoint
                pass

            return True
        return False

    def _find_breakpoint(self, filename, lineno):
        """
        Find a breakpoint using multiple matching strategies.

        Ignition may use different paths for the same file:
        1. Full filesystem path: /Users/.../script-python/Test/code.py
        2. Internal path: script-python/Test/code.py
        3. Module-style: <module:Test>

        We try to match any of these.
        """
        # Strategy 1: Direct match
        if self.session.hasBreakpoint(filename, lineno):
            return self.session.getBreakpoint(filename, lineno)

        # Strategy 2: Extract module path and match files containing that pattern
        module_path = self._extract_module_path(filename)
        if module_path:
            # Try matching with paths that contain the module path
            # e.g., filename='<module:Test>' should match 'script-python/Test/code.py'
            pattern = 'script-python/' + module_path.replace('.', '/') + '/code.py'
            bp = self._find_breakpoint_by_pattern(pattern, lineno)
            if bp:
                return bp

        # Strategy 3: Try matching by filename suffix (last component)
        if 'script-python' in filename:
            # Extract the part after script-python
            parts = filename.split('script-python')
            if len(parts) > 1:
                suffix = 'script-python' + parts[-1]
                bp = self._find_breakpoint_by_pattern(suffix, lineno)
                if bp:
                    return bp

        # Strategy 4: Match by base filename
        if '/' in filename or '\\' in filename:
            base = filename.replace('\\', '/').split('/')[-1]
            if base and base != 'code.py':  # Avoid matching all code.py files
                bp = self._find_breakpoint_by_pattern(base, lineno)
                if bp:
                    return bp

        return None

    def _find_breakpoint_by_pattern(self, pattern, lineno):
        """Find a breakpoint where the file path contains the pattern."""
        # Access all breakpoints from the session to find matching files
        # We need to iterate through stored breakpoints
        try:
            # Get all file paths with breakpoints via Java getter
            all_breakpoints = self.session.getBreakpoints()
            for file_path in all_breakpoints.keySet():
                # Normalize for comparison
                norm_file = file_path.replace('\\', '/').lower()
                norm_pattern = pattern.replace('\\', '/').lower()

                if norm_pattern in norm_file or norm_file.endswith(norm_pattern):
                    bp_map = all_breakpoints.get(file_path)
                    if bp_map and bp_map.containsKey(lineno):
                        return bp_map.get(lineno)
        except Exception as e:
            # Log but continue - matching failure shouldn't break debugging
            pass

        return None

    def _handle_stop(self, frame, reason):
        """Handle stopping at a breakpoint or step."""
        self.current_frame = frame
        self.stop_reason = reason
        self._paused = True
        self._continue_mode = False  # Clear continue mode when stopped

        # Build stack frames
        stack_frames = self._build_stack_frames(frame)

        # Register frames for variable inspection
        self._register_frame_references(frame)

        # Update session with current state using helper method
        # to avoid classloader issues with importing Java classes
        self.session.setCurrentStackFramesFromPython(stack_frames)

        # Notify VS Code that we stopped
        self.session.notifyStopped(reason)

        # Wait for command from VS Code
        self._wait_for_command()

    def _wait_for_command(self):
        """Wait for a debug command from VS Code."""
        while self._paused and not self._terminate:
            # Wait for command with timeout (1 second)
            command = self.session.waitForCommand(1000)

            if command is None:
                continue

            # Use string comparison to avoid classloader issues with enum imports
            cmd_type_name = command.getType().name()

            if cmd_type_name == "CONTINUE":
                self._paused = False
                self._continue_mode = True  # Only stop at breakpoints
                self.session.clearReferences()
                self.session.notifyContinued()
                break

            elif cmd_type_name == "STEP_OVER":
                self._paused = False
                self._continue_mode = False  # Stop at next line
                self.set_next(self.current_frame)
                self.session.clearReferences()
                self.session.notifyContinued()
                break

            elif cmd_type_name == "STEP_INTO":
                self._paused = False
                self._continue_mode = False  # Stop at next line
                self.set_step()
                self.session.clearReferences()
                self.session.notifyContinued()
                break

            elif cmd_type_name == "STEP_OUT":
                self._paused = False
                self._continue_mode = False  # Stop at next line after return
                self.set_return(self.current_frame)
                self.session.clearReferences()
                self.session.notifyContinued()
                break

            elif cmd_type_name == "PAUSE":
                # Already paused
                pass

            elif cmd_type_name == "TERMINATE":
                self._terminate = True
                self._paused = False
                self.set_quit()
                break

    def _build_stack_frames(self, frame):
        """Build a list of stack frame info from the current frame."""
        frames = []
        f = frame
        depth = 0

        while f is not None:
            filename = self.canonic(f.f_code.co_filename)
            lineno = f.f_lineno
            func_name = f.f_code.co_name

            # Skip internal debugger frames - these don't exist on the user's filesystem
            if self._is_internal_frame(filename, f):
                f = f.f_back
                depth += 1
                continue

            frame_id = self._get_frame_id(f)

            # Try to extract module path from filename
            module_path = self._extract_module_path(filename)

            # Translate <module:X> to real file path for VS Code
            real_path = self._translate_to_real_path(filename, module_path)

            frames.append({
                'id': frame_id,
                'name': func_name if func_name != '<module>' else '<main>',
                'filePath': real_path,
                'line': lineno,
                'column': 0,
                'modulePath': module_path
            })

            f = f.f_back
            depth += 1

            # Limit stack depth to prevent issues
            if depth > 100:
                break

        return frames

    def _is_internal_frame(self, filename, frame=None):
        """Check if a frame is from internal debugger code that should be hidden."""
        # Skip frames from the debugger itself
        internal_patterns = [
            'flint_debugger.py',
            'flint_debugger',
            '/bdb.py',
            'bdb.pyc',
            '<string>',  # exec'd code
        ]
        norm_filename = filename.replace('\\', '/').lower()
        for pattern in internal_patterns:
            if pattern.lower() in norm_filename:
                return True

        # Check for wrapper code frames (stdout/stderr capture)
        if frame is not None:
            func_name = frame.f_code.co_name

            # Skip frames from _FlintOutputCapture class methods
            # When in write/flush method, check if 'self' has our category marker
            if func_name in ('write', 'flush'):
                f_locals = frame.f_locals
                if 'self' in f_locals:
                    self_obj = f_locals['self']
                    # Check if this is our _FlintOutputCapture instance
                    if hasattr(self_obj, 'category') and hasattr(self_obj, 'session'):
                        return True

            # Skip any function whose name starts with _flint_ (our internal helpers)
            if func_name.startswith('_flint_') or func_name.startswith('_Flint'):
                return True

        return False

    def _translate_to_real_path(self, filename, module_path):
        """
        Translate internal Ignition paths like <module:Test> to real filesystem paths.

        Uses stored breakpoints to find the project base path, then constructs
        the path for any module (even those without breakpoints).
        """
        # If it's already a real path, return it
        if not filename.startswith('<'):
            return filename

        if not module_path:
            return filename

        try:
            all_breakpoints = self.session.getBreakpoints()
            target_pattern = 'script-python/' + module_path.replace('.', '/') + '/code.py'

            # Strategy 1: Direct match - check if this exact module has breakpoints
            for file_path in all_breakpoints.keySet():
                norm_file = file_path.replace('\\', '/').lower()
                norm_pattern = target_pattern.replace('\\', '/').lower()

                if norm_pattern in norm_file:
                    return file_path

            # Strategy 2: Extract project base from any breakpoint and construct path
            # This handles stepping into modules that don't have breakpoints
            for file_path in all_breakpoints.keySet():
                norm_file = file_path.replace('\\', '/')
                idx = norm_file.lower().find('/script-python/')
                if idx != -1:
                    # Extract base path (everything up to and including /ignition/)
                    # e.g., /path/to/projects/test/ignition/script-python/Test/code.py
                    #       becomes /path/to/projects/test/ignition/
                    base_path = norm_file[:idx + 1]  # Include the trailing slash before script-python
                    # Construct path for the target module
                    constructed_path = base_path + 'script-python/' + module_path.replace('.', '/') + '/code.py'
                    return constructed_path

        except Exception:
            pass

        # Fallback: return original filename
        return filename

    def _get_frame_id(self, frame):
        """Get or create a frame ID for the given frame."""
        if frame not in self.frame_to_id:
            self.frame_id_counter += 1
            frame_id = self.frame_id_counter
            self.frame_to_id[frame] = frame_id
            self.id_to_frame[frame_id] = frame
        return self.frame_to_id[frame]

    def get_frame_by_id(self, frame_id):
        """Get a frame by its ID."""
        return self.id_to_frame.get(frame_id)

    def _register_frame_references(self, frame):
        """Register frame references for variable inspection."""
        f = frame
        while f is not None:
            frame_id = self._get_frame_id(f)

            # Use helper method on session to avoid classloader issues
            # when running in Gateway's ScriptManager context
            self.session.createAndRegisterFrameReference(frame_id, f.f_locals, f.f_globals)

            f = f.f_back

    def _extract_module_path(self, filename):
        """Extract Ignition module path from filename if possible."""
        # Handle <module:ModuleName> format (Ignition's internal module reference)
        if filename.startswith('<module:') and filename.endswith('>'):
            # Extract module name from <module:Test> or <module:Folder/SubModule>
            module_name = filename[8:-1]  # Strip '<module:' and '>'
            return module_name

        # Look for patterns like /script-python/Shared/MyModule/code.py
        if 'script-python' in filename:
            parts = filename.split('script-python')
            if len(parts) > 1:
                path = parts[1].strip('/')
                # Remove code.py suffix
                if path.endswith('/code.py'):
                    path = path[:-8]
                # Convert path separators to dots
                return path.replace('/', '.')
        return None

    def run_code(self, code, globals_dict=None, locals_dict=None, filename='<flint-debug>'):
        """
        Run code under debugger control.

        Args:
            code: The Python code to execute
            globals_dict: Global namespace
            locals_dict: Local namespace
            filename: The filename for error reporting
        """
        if globals_dict is None:
            globals_dict = {}
        if locals_dict is None:
            locals_dict = globals_dict

        # Compile the code
        try:
            compiled = compile(code, filename, 'exec')
        except SyntaxError as e:
            raise

        # Set up tracing and run
        self.reset()
        sys.settrace(self.trace_dispatch)

        try:
            exec(compiled, globals_dict, locals_dict)
        except SystemExit:
            # Debug session terminated
            pass
        except Exception as e:
            # Re-raise the exception but notify about it first
            self.session.notifyOutput("stderr", traceback.format_exc())
            raise
        finally:
            sys.settrace(None)
            self.session.notifyTerminated()

    def enable_tracing(self):
        """
        Enable debug tracing without running code.
        Call this before using ScriptManager.runCode() to debug with proper namespace.
        Must call disable_tracing() when done.

        Note: stdout/stderr capture is handled at the Java level in DebugScriptHandler
        because ScriptManager.runCode() uses its own stdout context.
        """
        self.reset()
        # Set continue mode - only stop at breakpoints, not every line
        self._continue_mode = True
        sys.settrace(self.trace_dispatch)

    def disable_tracing(self):
        """
        Disable debug tracing and notify termination.
        Call this after code execution completes.
        """
        # Set shutdown flag first to prevent tracing into this method
        self._shutting_down = True
        sys.settrace(None)
        self.session.notifyTerminated()

    def pause(self):
        """Request the debugger to pause at the next opportunity."""
        # Set step mode to stop at the next line
        self.set_step()

    def terminate(self):
        """Terminate the debug session."""
        self._terminate = True
        self._paused = False
        self.set_quit()


def get_variables_from_dict(d, session, max_items=1000):
    """
    Convert a Python dictionary to a list of Variable objects.

    Args:
        d: The dictionary to convert
        session: The DebugSession for registering variable references
        max_items: Maximum number of items to return

    Returns:
        List of Variable Java objects
    """
    from java.util import ArrayList
    from dev.bwdesigngroup.flint.common.protocol.methods.debug import DebugVariablesResult

    variables = ArrayList()
    count = 0

    for name, value in d.items():
        if count >= max_items:
            break

        # Skip internal names
        if name.startswith('__') and name.endswith('__'):
            continue

        var_info = _create_variable_info(name, value, session)
        var = DebugVariablesResult.Variable(
            var_info['name'],
            var_info['value'],
            var_info['type'],
            var_info['variablesReference']
        )
        if var_info.get('namedVariables'):
            var.setNamedVariables(var_info['namedVariables'])
        if var_info.get('indexedVariables'):
            var.setIndexedVariables(var_info['indexedVariables'])

        variables.add(var)
        count += 1

    return variables


def get_variables_from_object(obj, session, max_items=1000):
    """
    Get variables from a Python object for expansion.

    Args:
        obj: The Python object to inspect
        session: The DebugSession for registering variable references
        max_items: Maximum number of items to return

    Returns:
        List of Variable Java objects
    """
    from java.util import ArrayList
    from dev.bwdesigngroup.flint.common.protocol.methods.debug import DebugVariablesResult

    variables = ArrayList()
    count = 0

    # Handle different object types
    if isinstance(obj, dict):
        for key, value in obj.items():
            if count >= max_items:
                break

            var_info = _create_variable_info(str(key), value, session)
            var = DebugVariablesResult.Variable(
                var_info['name'],
                var_info['value'],
                var_info['type'],
                var_info['variablesReference']
            )
            variables.add(var)
            count += 1

    elif isinstance(obj, (list, tuple)):
        for i, value in enumerate(obj):
            if count >= max_items:
                break

            var_info = _create_variable_info(str(i), value, session)
            var = DebugVariablesResult.Variable(
                var_info['name'],
                var_info['value'],
                var_info['type'],
                var_info['variablesReference']
            )
            variables.add(var)
            count += 1

    else:
        # Get object attributes
        try:
            for name in dir(obj):
                if count >= max_items:
                    break

                # Skip internal attributes
                if name.startswith('_'):
                    continue

                try:
                    value = getattr(obj, name)
                    # Skip methods
                    if callable(value):
                        continue

                    var_info = _create_variable_info(name, value, session)
                    var = DebugVariablesResult.Variable(
                        var_info['name'],
                        var_info['value'],
                        var_info['type'],
                        var_info['variablesReference']
                    )
                    variables.add(var)
                    count += 1
                except:
                    pass
        except:
            pass

    return variables


def _create_variable_info(name, value, session):
    """Create variable info dict from a Python value."""
    info = {
        'name': name,
        'value': _format_value(value),
        'type': type(value).__name__,
        'variablesReference': 0
    }

    # Check if value can be expanded
    if isinstance(value, dict):
        if len(value) > 0:
            info['variablesReference'] = session.registerVariableReference(value)
            info['namedVariables'] = len(value)
    elif isinstance(value, (list, tuple)):
        if len(value) > 0:
            info['variablesReference'] = session.registerVariableReference(value)
            info['indexedVariables'] = len(value)
    elif hasattr(value, '__dict__') and not callable(value):
        # Complex object that can be expanded
        try:
            attrs = [a for a in dir(value) if not a.startswith('_') and not callable(getattr(value, a, None))]
            if len(attrs) > 0:
                info['variablesReference'] = session.registerVariableReference(value)
                info['namedVariables'] = len(attrs)
        except:
            pass

    return info


def _format_value(value, max_len=200):
    """Format a Python value for display."""
    try:
        if value is None:
            return "None"
        elif isinstance(value, bool):
            return str(value)
        elif isinstance(value, (int, float)):
            return str(value)
        elif isinstance(value, str):
            if len(value) > max_len:
                return repr(value[:max_len] + "...")
            return repr(value)
        elif isinstance(value, (list, tuple)):
            type_name = type(value).__name__
            return "%s[%d]" % (type_name, len(value))
        elif isinstance(value, dict):
            return "dict{%d}" % len(value)
        else:
            s = repr(value)
            if len(s) > max_len:
                return s[:max_len] + "..."
            return s
    except:
        return "<error>"


def evaluate_expression(expression, frame_locals, frame_globals):
    """
    Evaluate an expression in the given context.

    Args:
        expression: The expression to evaluate
        frame_locals: Local namespace
        frame_globals: Global namespace

    Returns:
        The result of evaluation
    """
    return eval(expression, frame_globals, frame_locals)
