public class Token {
    private String symbol;
    private String token;
    private int pos=0;
    private int row;
    private int col;
    private SymItem symItem = new SymItem(null, 0, null, null, STIType.invalid, DataType.invalid, 0, 0, 0, 0, null);

    public Token(String symbol, String token, int pos, int row, int col) {
        this.symbol = symbol;
        this.token = token;
        this.pos = pos;
        this.row = row;
        this.col = col;
    }

    public void setSymItem(SymItem symItem) {
        this.symItem = symItem;
    }

    public SymItem getSymItem() {
        return symItem;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getToken() {
        return token;
    }

    public int getPos() {
        return pos;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public String toString() {
        return this.getSymbol()+" "+this.getToken()+"\n";
    }
}
