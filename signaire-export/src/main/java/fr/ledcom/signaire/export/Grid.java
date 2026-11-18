package fr.ledcom.signaire.export;

/** Columns and rows of the signs of a page. */
record Grid(int columns, int rows) {

    /** A grid of the given number of cells, with more rows than columns on portrait pages. */
    static Grid of(int cells, boolean landscape) {
        Grid portrait = switch (cells) {
            case 1 -> new Grid(1, 1);
            case 2 -> new Grid(1, 2);
            case 4 -> new Grid(2, 2);
            case 6 -> new Grid(2, 3);
            case 8 -> new Grid(2, 4);
            case 9 -> new Grid(3, 3);
            case 12 -> new Grid(3, 4);
            default -> throw new IllegalArgumentException("No grid of " + cells + " cells");
        };
        return landscape ? new Grid(portrait.rows(), portrait.columns()) : portrait;
    }

    int cells() {
        return columns * rows;
    }
}
