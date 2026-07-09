package dev.bwdesigngroup.flint.gateway.lsp;

import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps between character offsets (Jython AST uses {@code getCharStartIndex()}/{@code
 * getCharStopIndex()}) and LSP {@link Position} (0-based line + character), for a given document
 * text. Reused by symbols, hover, definition, and completion.
 */
public class LineIndex {

    private final int[] lineStartOffsets;
    private final int length;

    public LineIndex(String text) {
        String s = text != null ? text : "";
        this.length = s.length();
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        this.lineStartOffsets = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) {
            this.lineStartOffsets[i] = starts.get(i);
        }
    }

    /** Converts a 0-based char offset to an LSP Position. Clamps out-of-range offsets. */
    public Position positionAt(int offset) {
        int off = Math.max(0, Math.min(offset, length));
        int line = findLine(off);
        int character = off - lineStartOffsets[line];
        return new Position(line, character);
    }

    /** Converts an LSP Position to a 0-based char offset. Clamps out-of-range input. */
    public int offsetAt(int line, int character) {
        if (line < 0) {
            return 0;
        }
        if (line >= lineStartOffsets.length) {
            return length;
        }
        int base = lineStartOffsets[line];
        int off = base + Math.max(0, character);
        return Math.max(0, Math.min(off, length));
    }

    private int findLine(int offset) {
        int lo = 0;
        int hi = lineStartOffsets.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStartOffsets[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
