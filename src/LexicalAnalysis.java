import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class LexicalAnalysis {
    //record
    private String raw; //最开始的输入
    private char ch; //当前的字符
    private String token; //当前的字符串
    private int num; //当前的整型
    private String symbol = ""; //当前识别的单词类型
    private int state = 0; // in 注释
    //extra
    private int pos = 0; //指针位置
    private int line = 1; //当前行数
    private int column = 1; //当前列数
    private int len = 0; // raw长度
    private ArrayList<String> ret = new ArrayList<>();
    private final Map<String, String> reserved = new TreeMap<String, String>() {{
        put("main", "MAINTK");
        put("const", "CONSTTK");
        put("int", "INTTK");
        put("break", "BREAKTK");
        put("continue", "CONTINUETK");
        put("if", "IFTK");
        put("else", "ELSETK");
        put("while", "WHILETK");
        put("getint", "GETINTTK");
        put("printf", "PRINTFTK");
        put("return", "RETURNTK");
        put("void", "VOIDTK");
    }};
    private final Map<Character, String> normal = new TreeMap<Character, String>() {{
        put('+', "PLUS");
        put('-', "MINU");
        put('*', "MULT");
        put('%', "MOD");
        put(';', "SEMICN");
        put(',', "COMMA");
        put('(', "LPARENT");
        put(')', "RPARENT");
        put('[', "LBRACK");
        put(']', "RBRACK");
        put('{', "LBRACE");
        put('}', "RBRACE");
    }};

    public LexicalAnalysis(String raw) {
        this.raw = raw;
        len = raw.length();
        /*
        while (pos < raw.length()) {
            getToken();
            if (!symbol.equals("")) {
                ret.add(symbol + " " + token);
            }
        }
        */
    }

    public void error(String eid, String info) {
        //System.out.println(line +" "+ eid);
        Error.add(line, eid, info);
    }

    public Token graspToken() {
        //TODO: error here
        getToken();
        while (symbol.equals("") && pos < raw.length()) {
            getToken();
        }
        ret.add(symbol + " " + token);
        return new Token(symbol, token, pos, line, column);
    }

    public String getAnswer() {
        return String.join("\n", ret);
    }


    public int getToken() {
        token = "";
        symbol = "";
        getChar();
        while (isWhite() && pos < len) {
            getChar();
        }
        if (pos > len) {
            return 0;
        } else if (pos == len && isWhite()) {
            return 0;
        }
        //TODO: Attention! 如果pos在这里已经到文件尾了 说明文件是空的
        if (isNonDigit()) {
            while (isNonDigit() || isDigit()) {
                token = token + ch;
                getChar();
            }
            retract();
            symbol = reserve();
        } else if (isDigit()) {
            token += ch;
            if (ch == '0') {
                if (this.pos < this.len) {
                    getChar();
                    if (isDigit()) {
                        System.out.println("Error!");
                    }
                    retract();
                }
            } else {
                if (this.pos < this.len) {
                    getChar();
                    while (this.pos != this.len && isDigit()) {
                        token = token + ch;
                        getChar();
                    }
                    retract();
                }
            }
            //System.out.println("token:" + token);
            num = Integer.parseInt(token);
            symbol = "INTCON";
        } else if (!checkNormal().equals("NOTNORMAL")) {
            symbol = checkNormal();
            token += ch;
        } else if (ch == '"') {
            SymTable.formatCount = 0;
            token += ch;
            do {
                if (pos == len) {
                    System.out.println("Error");
                    return 1;
                }
                getChar();
                token += ch;
                if (ch == '"') {
                    break;
                } else if (ch == '\\') {
                    if (pos == len) {
                        System.out.println("Error");
                        return 1;
                    }
                    getChar();
                    token += ch;
                    if (ch != 'n') {
                        error("a", "expect 'n' after '\\'");
                    }
                } else if (ch == '%') {
                    if (pos == len) {
                        System.out.println("Error");
                        return 1;
                    }
                    getChar();
                    token += ch;
                    if (ch != 'd') {
                        error("a", "expect 'd' after '%'");
                    } else {
                        SymTable.formatCount++;
                    }
                } else if (!(ch == 32 || ch == 33 || 40 <= ch && ch <= 126)) {
                    error("a", "nothing match in stringFormat");
                }
            } while (true);
            symbol = "STRCON";
        } else if (ch == '/') {
            state = 0;
            while (true) {
                if (state == 0) {
                    if (pos == len) {
                        System.out.println("Error");
                        return 1;
                    }
                    getChar();
                    if (ch == '/') {
                        state = 1;
                    } else if (ch == '*') {
                        state = 2;
                    } else {
                        token = "/";
                        symbol = "DIV";
                        retract();
                        break;
                    }
                } else if (state == 1) {
                    if (pos == len) {
                        break;
                    }
                    getChar();
                    if (ch == '\n') {
                        break;
                    }
                } else if (state == 2) {
                    if (pos == len) {
                        System.out.println("Error");
                        return 1;
                    }
                    getChar();
                    if (ch == '*') {
                        state = 3;
                    }
                } else if (state == 3) {
                    if (pos == len) {
                        System.out.println("Error");
                        return 1;
                    }
                    getChar();
                    if (ch == '/') {
                        break;
                    } else if (ch == '*') {
                        state = 3;
                    } else {
                        state = 2;
                    }
                }
            }
            state = 0;
        } else if (ch == '!') {
            if (len == pos) {
                symbol = "NOT";
                token = "!";
                //    System.out.println("Error");
                return 1;
            }
            getChar();
            if (ch == '=') {
                symbol = "NEQ";
                token = "!=";
            } else {
                retract();
                symbol = "NOT";
                token = "!";
            }
        } else if (ch == '&') {

            if (len == pos) {
                System.out.println("Error");
                return 1;
            }
            getChar();
            if (ch == '&') {
                symbol = "AND";
                token = "&&";
            } else {
                retract();
                System.out.println("here in ch==&, error");
                //TODO: add error
            }
        } else if (ch == '|') {

            if (len == pos) {
                System.out.println("Error");
                return 1;
            }
            getChar();
            if (ch == '|') {
                symbol = "OR";
                token = "||";
            } else {
                retract();
                System.out.println("here in ch==|, error");
                //TODO:add error
            }
        } else if (ch == '<') {

            if (len == pos) {
                symbol = "LSS";
                token = "<";
                //    System.out.println("Error");
                return 1;
            }
            getChar();
            if (ch == '=') {
                symbol = "LEQ";
                token = "<=";
            } else {
                retract();
                symbol = "LSS";
                token = "<";
            }
        } else if (ch == '>') {

            if (len == pos) {
                symbol = "GRE";
                token = ">";
                //    System.out.println("Error");
                return 1;
            }
            getChar();
            if (ch == '=') {
                symbol = "GEQ";
                token = ">=";
            } else {
                retract();
                symbol = "GRE";
                token = ">";
            }
        } else if (ch == '=') {
            if (len == pos) {
                symbol = "ASSIGN";
                token = "=";
                //    System.out.println("Error");
                return 1;
            }
            getChar();
            if (ch == '=') {
                symbol = "EQL";
                token = "==";
            } else {
                retract();
                symbol = "ASSIGN";
                token = "=";
            }
        } else {
            symbol = "INVALID";
            System.out.println("nothing match");
            //TODO: add error
        }
        return 1;
    }

    private void getChar() {
        if (pos >= raw.length()) {
            //TODO: Attention! Throw an Exception here
            System.out.println("error here at getChar()");
        }
        ch = raw.charAt(pos);
        pos++;
        if (ch == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    private boolean isWhite() {
        return Character.isWhitespace(ch);
    }

    private boolean isNonDigit() {
        return (Character.isLowerCase(ch) || Character.isUpperCase(ch) || ch == '_');
    }

    private boolean isDigit() {
        return Character.isDigit(ch);
    }

    private void retract() {
        pos--;
        column--;
        if (ch == '\n') {
            line--;
            column = 1;
        }
    }

    private String reserve() {
        if (reserved.containsKey(token)) {
            return reserved.get(token);
        }
        return "IDENFR";
    }

    private String checkNormal() {
        if (normal.containsKey(ch)) {
            return normal.get(ch);
        }
        return "NOTNORMAL";
    }


}
