package fr.ledcom.signaire.export;

/** A rectangle on a PDF page, from its lower left corner, in points. */
record Box(float x, float y, float width, float height) {

    float top() {
        return y + height;
    }

    float right() {
        return x + width;
    }

    /** The box without a margin on each side. */
    Box inset(float margin) {
        return new Box(x + margin, y + margin, Math.max(0, width - 2 * margin), Math.max(0, height - 2 * margin));
    }

    /** Cells of a grid, row by row from the top left. */
    Box cell(Grid grid, int index) {
        float cellWidth = width / grid.columns();
        float cellHeight = height / grid.rows();
        int column = index % grid.columns();
        int row = index / grid.columns();
        return new Box(x + column * cellWidth, top() - (row + 1) * cellHeight, cellWidth, cellHeight);
    }
}
