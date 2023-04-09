
import java.util.*;

public class GA {
    private static String raw;
    public static Token tk;
    public static int point = 0;
    public static String sym;
    private static ArrayList<Token> cur_lex = new ArrayList<>();
    private static LexicalAnalysis lex;
    private static String ans = null;

    public static String cur_func = "GLOBAL";
    public static int LOCAL_ADDR = 0;
    public static int local_addr = Memory.DATA_BASE;
    ;
    public static final int INTSIZE = 4;


    public GA(String raw) {
        GA.raw = raw;
        lex = new LexicalAnalysis(GA.raw);
        CompUnit cp = new CompUnit();
        ans = cp.toString();
    }

    public String getRet() {
        return ans;
    }

    public static void getSym() {
        if (point < cur_lex.size()) {
            tk = cur_lex.get(point);
        } else {
            if (tk != null && tk.getPos() >= raw.length()) {
                return;
            }
            tk = lex.graspToken();
            if (tk.getSymbol().equals("")) { //空字符串，对应那边已经读完了，有待改进
                return;
            }
            if (tk.getSymbol().equals("INVALID")) {
                System.out.println("exception in getSym");
            }
            cur_lex.add(tk);
        }
        point++;
        sym = tk.getSymbol();
    }

    public static void retract() {
        point--;
        tk = cur_lex.get(point - 1);
        sym = tk.getSymbol();
    }

    public static void skip(Integer record) {
        point = record;
        tk = cur_lex.get(point - 1);
        sym = tk.getSymbol();
        //System.out.println(ret);
    }

    public static Token getPre() {
        if (point <= 1) {
            return tk;
        }
        return cur_lex.get(point - 2); //因为point-1才是这次的tk
    }

    public static void error(String eid, String info, int line) {
        System.out.println(line + " " + eid + " " + info);
        Error.add(line, eid, info);
        //System.exit(0);
    }

    public static String add_midCode(OPType op, String num1, String num2, String result) {
        return PseudoCodes.add(op, num1, num2, result);
    }

    public static String add_midTmp(OPType op, String num1, String num2, String result,
                                    String cur_func) {
        return PseudoCodes.addTmp(op, num1, num2, result, cur_func);
    }
}


class CompUnit extends Root {
    private LinkedList<Root> Decls = new LinkedList<>();
    private LinkedList<Root> FuncDefs = new LinkedList<>();
    private Root MainFuncDef = null;

    @Override
    RootType getType() {
        return RootType.CompUnit;
    }

    private void getWord() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public CompUnit() {
        SymTable.addLayer(); //第0层也记录
        getWord();
        while (GA.sym.equals("INTTK") || GA.sym.equals("VOIDTK") || GA.sym.equals("CONSTTK")) {
            if (GA.sym.equals("CONSTTK")) {
                Decls.addLast(new ConstDecl());
                continue;
            }
            getWord(); //int a
            getWord(); //int a[, int a(, int a=,
            if (!GA.sym.equals("LPARENT")) {
                retract(); //int a
                retract(); //int
                Decls.addLast(new VarDecl());
            } else {
                retract();
                retract(); // int/void
                GA.local_addr = GA.LOCAL_ADDR;
                while (GA.sym.equals("INTTK") || GA.sym.equals("VOIDTK")) {
                    if (GA.sym.equals("INTTK")) {
                        getWord();
                        if (GA.sym.equals("MAINTK")) { //int main
                            retract();
                            this.MainFuncDef = new MainFuncDef();
                            SymTable.popLayer();
                            return;
                        } else { //int other
                            retract();
                            FuncDefs.addLast(new FuncDef()); //int balabala function
                        }
                    } else { // void balabala
                        FuncDefs.addLast(new FuncDef());
                    }
                }
            }
            //getSym();
        }
    }

    public String toString() {
        StringBuilder ans = new StringBuilder();
        for (Root decl : this.Decls) {
            ans.append(decl.toString());
        }
        for (Root funcDec : this.FuncDefs) {
            ans.append(funcDec.toString());
        }
        ans.append(this.MainFuncDef.toString());
        ans.append("<CompUnit>\n");
        return ans.toString();
    }

}

class ConstDecl extends Root {
    private LinkedList<ConstDef> ConstDefs = new LinkedList<>();
    StringBuilder ans = new StringBuilder();

    @Override
    RootType getType() {
        return RootType.ConstDecl;
    }

    private void getWord() {
        GA.getSym();
    }

    public ConstDecl() {
        //const
        ans.append(GA.tk.toString());
        getWord();
        //int
        ans.append(GA.tk.toString());
        getWord();
        this.ConstDefs.addLast(new ConstDef());
        while (GA.sym.equals("COMMA")) {
            getWord();
            this.ConstDefs.addLast(new ConstDef());
        }
        if (!GA.sym.equals("SEMICN")) {
            GA.error("i", "expect ';' in ConstDecl", GA.getPre().getRow()); //TODO:check,应该是前一个元素的行数
            GA.retract();
        }
        for (ConstDef cd : this.ConstDefs) {

        }
        getWord();
    }

    @Override
    public String toString() {
        Iterator<ConstDef> iter = this.ConstDefs.iterator();
        ans.append((iter.next()).toString());
        while (iter.hasNext()) {
            ans.append("COMMA ,\n");
            ans.append(((Root) iter.next()).toString());
        }
        ans.append("SEMICN ;\n<ConstDecl>\n");
        return ans.toString();
    }
}

class ConstDef extends Root {
    private Token Ident;
    private LinkedList<ConstExp> ConstExps = new LinkedList<>();
    private ConstInitVal ConstInitVal = null;
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.ConstDef;
    }

    private void getWord() {
        GA.getSym();
    }

    public ConstDef() {
        if (!GA.sym.equals("IDENFR")) {
            //error("ConstDef");
        }
        Ident = GA.tk;
        while (true) {
            getWord();
            if (GA.sym.equals("ASSIGN")) {
                getWord();
                ConstInitVal = new ConstInitVal();
                SymTable.addConst(Ident, dim, GA.cur_func, GA.local_addr);
                break;
            } else if (GA.sym.equals("LBRACK")) {
                if (dim == 2) {
                    //error("ConstDef");
                }
                getWord();
                ConstExps.addLast(new ConstExp());
                if (!GA.sym.equals("RBRACK")) {
                    GA.error("k", "expect ']' in ConstDef", GA.getPre().getRow());
                    GA.retract();
                }
                dim++;
            } else {
                //error("ConstDef");
                break;
            }//error
        }
        saveConstDef();
    }

    /**
     * 我们默认，对于数组定义，每一个维度具体是多少（也就是dim1和dim2），是可以算出值的
     * actually, 我们甚至不需要知道dim1和dim2具体是多少，因为只要存进去就知道了
     * 所以我算dim1和dim2纯属检验，无实质意义
     */
    public void saveConstDef() {
        String constName = this.Ident.getToken();
        int size = 0;
        if (dim == 0) { //0维变量
            String value = this.ConstInitVal.calConstInitVal();
            SymTable.assignConst(constName, Integer.parseInt(value));
            GA.add_midCode(OPType.CONST, constName, value, "DEFAULT");
            size = GA.INTSIZE;
        } else if (dim == 1) {
            String dim1 = this.ConstExps.get(0).calConstExp();
            if (!utlis.isInteger(dim1)) {
                System.out.println("in constDef, 一维数组 dim1 is not an integer!");
            }
            GA.add_midCode(OPType.ARR, constName, dim1, "DEFAULT");
            SymTable.updateConstDims(constName, Integer.parseInt(dim1), 0);
            //下面的这个容器，里面每个ConstInitVal都对应一个ConstExp
            LinkedList<ConstInitVal> constInitVals = this.ConstInitVal.getConstInitVals();
            String value;
            int count = 0; //count对应数组下标
            for (ConstInitVal ci : constInitVals) {
                value = ci.calConstInitVal();
                SymTable.assignConst(constName, Integer.parseInt(value));
                GA.add_midCode(OPType.ARR_SAVE, constName, String.valueOf(count), value);
                count++;
            }
            if (constInitVals.size() != Integer.parseInt(dim1)) {
                System.out.println("in constDef, def num doesn't equal to real size");
            }
            size = GA.INTSIZE * Integer.parseInt(dim1);
        } else {
            String dim1 = this.ConstExps.get(0).calConstExp();
            String dim2 = this.ConstExps.get(1).calConstExp();
            int dimTotal, count = 0;
            String value;
            if (!utlis.isInteger(dim1)) {
                System.out.println("in constDef, 二维数组 dim1 is not an integer!");
            }
            if (!utlis.isInteger(dim2)) {
                System.out.println("in constDef, 二维数组 dim2 is not an integer!");
            }
            dimTotal = utlis.calInteger(dim1, dim2, "*");
            GA.add_midCode(OPType.ARR, constName, String.valueOf(dimTotal), "DEFAULT");
            SymTable.updateConstDims(constName, Integer.parseInt(dim1), Integer.parseInt(dim2));
            LinkedList<ConstInitVal> outerConstInitVals = this.ConstInitVal.getConstInitVals(); //这个是一维的
            for (ConstInitVal ci : outerConstInitVals) {
                LinkedList<ConstInitVal> innerConstInitVals = ci.getConstInitVals(); //这个里面的每个constinitval对应一堆constExp
                for (ConstInitVal ici : innerConstInitVals) {
                    value = ici.calConstInitVal();
                    SymTable.assignConst(constName, Integer.parseInt(value));
                    GA.add_midCode(OPType.ARR_SAVE, constName, String.valueOf(count), value);
                    count++;
                }
            }
            if (dimTotal != count) {
                System.out.println("in constDef, 二维数组 dimTotal doesn't equal to real size!");
            }
            size = GA.INTSIZE * dimTotal;
        }
        if (GA.cur_func.equals("GLOBAL")) {
            GA.local_addr = Memory.addGlobal(constName, size);
        } else {
            GA.local_addr = Memory.addTmp(GA.cur_func, size);
            SymTable.changeLocalAddr(GA.cur_func, this.Ident.getToken(), GA.local_addr);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.Ident.toString());
        for (Root constExp : this.ConstExps) {
            builder.append("LBRACK [\n");
            builder.append(constExp.toString());
            builder.append("RBRACK ]\n");
        }
        builder.append("ASSIGN =\n");
        builder.append(this.ConstInitVal.toString());
        builder.append("<ConstDef>\n");
        return builder.toString();
    }
}

class VarDecl extends Root {
    private LinkedList<Root> VarDefs = new LinkedList<>();
    StringBuilder ans = new StringBuilder();

    private void getWord() {
        GA.getSym();
    }

    @Override
    RootType getType() {
        return RootType.VarDecl;
    }

    public VarDecl() {
        if (!GA.sym.equals("INTTK")) {
            //error("VarDecl-1");
        }
        ans.append(GA.tk.toString());
        getWord();
        VarDefs.addLast(new VarDef());
        while (GA.sym.equals("COMMA")) {
            getWord();
            VarDefs.addLast(new VarDef());
        }
        if (!GA.sym.equals("SEMICN")) {
            GA.error("i", "expect ';' in VarDecl", GA.getPre().getRow());
            GA.retract();
        }
        getWord();
    }

    @Override
    public String toString() {
        Iterator<Root> iter = this.VarDefs.iterator();
        ans.append((iter.next()).toString());
        while (iter.hasNext()) {
            ans.append("COMMA ,\n");
            ans.append((iter.next()).toString());
        }
        ans.append("SEMICN ;\n<VarDecl>\n");
        return ans.toString();
    }
}

class FuncDef extends Root {
    private Root FuncType = null;
    private Token Ident = null;
    private Root FuncFParams = null;
    private Block Block = null;

    private void getWord() {
        GA.getSym();
    }

    @Override
    RootType getType() {
        return RootType.FuncDef;
    }

    public FuncDef() {
        DataType dataType;
        boolean hasReturn = false;
        if (GA.sym.equals("INTTK")) {
            dataType = DataType.int_;
            SymTable.intCount = 1;
            SymTable.voidCount = 0;
        } else {
            dataType = DataType.void_;
            SymTable.voidCount = 1;
            SymTable.intCount = 0;
        }
        this.FuncType = new FuncType(); // int / void
        this.Ident = GA.tk;
        GA.cur_func = this.Ident.getToken(); //cur_func变成当前的function了
        GA.add_midCode(OPType.FUNC, dataType.toString(), GA.tk.getToken(), "DEFAULT");
        SymTable.addFunName(GA.tk, dataType, GA.local_addr);
        SymTable.addLayer();
        getWord();
        if (!GA.sym.equals("LPARENT")) { // (
            //error("FuncDef");
        }
        getWord();
        if (!GA.sym.equals("RPARENT")) { // funcfparams
            this.FuncFParams = new FuncFParams();
            if (!GA.sym.equals("RPARENT")) {
                GA.error("j", "expect ')' in FuncDef", GA.getPre().getRow());
                GA.retract();
            }
        }
        getWord();
        this.Block = new Block();
        GA.retract();
        hasReturn = (this.Block).isHasReturn();
        if (SymTable.intCount == 1 && !hasReturn) { //int function without return
            GA.error("g", "expect return in an int function", GA.tk.getRow());
        }
        getWord();
        /**
         * 对于函数的返回值，规定如下：
         * 1.如果有返回值，则ret返回值
         * 2.如果没有返回值，例如return; 或者压根没有写，则强行加一个ret
         */
        if (!this.Block.isHasReturn()) {
            GA.add_midCode(OPType.RETURN, "DEFAULT", "DEFAULT", "DEFAULT");
        }
        GA.add_midCode(OPType.END_FUNC, dataType.toString(), this.Ident.getToken(), "DEFAULT");
        GA.local_addr = GA.LOCAL_ADDR;
        SymTable.popLayer();
        SymTable.voidCount = 0;
        SymTable.intCount = 0;
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        ans.append(this.FuncType);
        ans.append(this.Ident);
        ans.append("LPARENT (\n");
        if (this.FuncFParams != null) {
            ans.append(this.FuncFParams);
        }
        ans.append("RPARENT )\n");
        ans.append(this.Block);
        ans.append("<FuncDef>\n");
        return ans.toString();
    }
}

class ConstInitVal extends Root {
    private ConstExp ConstExp = null;
    private LinkedList<ConstInitVal> ConstInitVals = new LinkedList<>();

    @Override
    RootType getType() {
        return RootType.ConstInitVal;
    }

    private void getWord() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public ConstInitVal() {
        if (GA.sym.equals("LBRACE")) { // {
            getWord();
            if (!GA.sym.equals("RBRACE")) { //not empty
                ConstInitVals.addLast(new ConstInitVal());
                while (GA.sym.equals("COMMA")) {
                    getWord();
                    ConstInitVals.addLast(new ConstInitVal());
                }
                if (!GA.sym.equals("RBRACE")) { //}
                    //error("ConstInitVal");
                }
            }
        } else {
            this.ConstExp = new ConstExp();
            retract();
        }
        getWord();
    }

    public LinkedList<ConstInitVal> getConstInitVals() {
        return ConstInitVals;
    }

    public String calConstInitVal() {
        return this.ConstExp.calConstExp();
    }

    public String toString() {
        StringBuilder ans = new StringBuilder();
        if (this.ConstExp != null) {
            ans.append(this.ConstExp.toString());
        } else {
            ans.append("LBRACE {\n");
            Iterator<ConstInitVal> iter = this.ConstInitVals.iterator();
            if (iter.hasNext()) {
                ans.append(iter.next());
            }
            while (iter.hasNext()) {
                ans.append("COMMA ,\n");
                ans.append(iter.next());
            }
            ans.append("RBRACE }\n");
        }
        ans.append("<ConstInitVal>\n");
        return ans.toString();
    }
}

class VarDef extends Root {
    private LinkedList<ConstExp> ConstExps = new LinkedList<>();
    private Token Ident = null;
    private InitVal InitVal = null;
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.VarDef;
    }

    private void getWord() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public VarDef() {
        if (!GA.sym.equals("IDENFR")) {
            //error("VarDef");
        }
        Ident = GA.tk;
        while (true) {
            getWord();
            if (GA.sym.equals("ASSIGN")) { //赋值
                getWord();
                this.InitVal = new InitVal();
                retract();
                break;
            } else if (GA.sym.equals("LBRACK")) { //数组
                if (dim == 2) {
                    retract();
                    //error("VarDef");
                }
                getWord();
                this.ConstExps.addLast(new ConstExp());
                if (!GA.sym.equals("RBRACK")) {
                    GA.error("k", "expect ']' in VarDef", GA.getPre().getRow());
                    GA.retract();
                }
                dim++;
            } else { //既不是=也不是[
                retract();
                break;
            }
        }
        SymTable.addVar(Ident, dim, GA.cur_func, GA.local_addr);
        saveVarDef();
        getWord();
    }

    /**
     * 我们默认，对于数组定义，每一个维度具体是多少（也就是dim1和dim2），是可以算出值的
     */
    public void saveVarDef() {
        String varName = this.Ident.getToken();
        String value = null;
        int size = 0;
        if (dim == 0) { //0维变量
            GA.add_midCode(OPType.VAR, varName, "DEFAULT", "DEFAULT");
            if (this.InitVal != null) {
                value = this.InitVal.calInitVal(); //对于0维变量，其值就是initval的exp
                GA.add_midCode(OPType.ASSIGN, varName, value, "DEFAULT");
            }
            size = GA.INTSIZE;
        } else if (dim == 1) {
            String dim1 = this.ConstExps.get(0).calConstExp();
            if (!utlis.isInteger(dim1)) {
                System.out.println("in varDef, 一维数组 dim1 is not an integer!");
            }
            GA.add_midCode(OPType.ARR, varName, dim1, "DEFAULT");
            SymTable.updateVarDims(varName, Integer.parseInt(dim1), 0);
            //下面的这个容器，里面每个InitVal都对应一个Exp
            if (this.InitVal != null) {
                LinkedList<InitVal> InitVals = this.InitVal.getInitVals();
                int count = 0; //count对应数组下标
                for (InitVal iv : InitVals) {
                    value = iv.calInitVal();
                    GA.add_midCode(OPType.ARR_SAVE, varName, String.valueOf(count), value);
                    count++;
                }
            }
            size = GA.INTSIZE * Integer.parseInt(dim1);
        } else {
            String dim1 = this.ConstExps.get(0).calConstExp();
            String dim2 = this.ConstExps.get(1).calConstExp();
            int dimTotal, count = 0;
            if (!utlis.isInteger(dim1)) {
                System.out.println("in varDef, 二维数组 dim1 is not an integer!");
            }
            if (!utlis.isInteger(dim2)) {
                System.out.println("in varDef, 二维数组 dim2 is not an integer!");
            }
            dimTotal = utlis.calInteger(dim1, dim2, "*");
            GA.add_midCode(OPType.ARR, varName, String.valueOf(dimTotal), "DEFAULT");
            SymTable.updateVarDims(varName, Integer.parseInt(dim1), Integer.parseInt(dim2));
            if (this.InitVal != null) {
                LinkedList<InitVal> outerInitVals = this.InitVal.getInitVals(); //这个是一维的
                for (InitVal iv : outerInitVals) {
                    LinkedList<InitVal> innerInitVals = iv.getInitVals(); //这个里面的每个initval对应一堆Exp
                    for (InitVal iiv : innerInitVals) {
                        value = iiv.calInitVal();
                        GA.add_midCode(OPType.ARR_SAVE, varName, String.valueOf(count), value);
                        count++;
                    }
                }
            }
            size = GA.INTSIZE * dimTotal;
        }
        if (GA.cur_func.equals("GLOBAL")) {
            GA.local_addr = Memory.addGlobal(varName, size);
        } else { //这块的符号表添加着实是个问题，对于局部数组，它的起始位置是最尾端，因此先算地址，再添加进去
            GA.local_addr = Memory.addTmp(GA.cur_func, size);
            SymTable.changeLocalAddr(GA.cur_func, this.Ident.getToken(), GA.local_addr);
            int y = 9;
        }
    }

    public String toString() {
        StringBuilder ans = new StringBuilder();
        ans.append(this.Ident.toString());
        for (Root constExp : this.ConstExps) {
            ans.append("LBRACK [\n");
            ans.append(constExp.toString());
            ans.append("RBRACK ]\n");
        }
        if (this.InitVal != null) {
            ans.append("ASSIGN =\n");
            ans.append(this.InitVal.toString());
        }
        ans.append("<VarDef>\n");
        return ans.toString();
    }
}

class InitVal extends Root {
    private LinkedList<InitVal> InitVals = new LinkedList<>();
    private Exp Exp = null;

    @Override
    RootType getType() {
        return RootType.InitVal;
    }

    private void getWord() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public InitVal() {
        if (GA.sym.equals("LBRACE")) {
            getWord();
            if (!GA.sym.equals("RBRACE")) {
                this.InitVals.addLast(new InitVal());
                while (GA.sym.equals("COMMA")) {
                    getWord();
                    this.InitVals.addLast(new InitVal());
                }
                if (!GA.sym.equals("RBRACE")) {
                    //error("InitVal");
                }
            }
        } else {
            this.Exp = new Exp();
            retract();
        }
        getWord();
    }

    public LinkedList<InitVal> getInitVals() {
        return InitVals;
    }

    public String calInitVal() {
        return this.Exp.calExp();
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        if (this.Exp != null) {
            ans.append(Exp.toString());
        } else {
            ans.append("LBRACE {\n");
            Iterator<InitVal> iter = this.InitVals.iterator();
            if (iter.hasNext()) {
                ans.append(iter.next());
            }
            while (iter.hasNext()) {
                ans.append("COMMA ,\n");
                ans.append(iter.next());
            }
            ans.append("RBRACE }\n");
        }
        ans.append("<InitVal>\n");
        return ans.toString();
    }
}

class FuncType extends Root {
    private Token type = null;

    @Override
    RootType getType() {
        return RootType.FuncType;
    }

    private void getWord() {
        GA.getSym();
    }

    public FuncType() {
        if (!GA.sym.equals("INTTK") && !GA.sym.equals("VOIDTK")) {
            //error("FuncType");
        }
        type = GA.tk;
        getWord();
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        ans.append(type.toString());
        ans.append("<FuncType>\n");
        return ans.toString();
    }
}

class MainFuncDef extends Root {
    private Root Block = null;
    boolean hasReturn = false;

    @Override
    RootType getType() {
        return RootType.MainFuncDef;
    }

    private void getWord() {
        GA.getSym();
    }

    public MainFuncDef() {
        GA.add_midCode(OPType.FUNC, DataType.int_.toString(), "main", "DEFAULT");
        if (!GA.sym.equals("INTTK")) { //int
            //error("MainFuncDef");
        }
        getWord();
        if (!GA.sym.equals("MAINTK")) { //main
            //error("MainFuncDef");
        }
        SymTable.addFunName(GA.tk, DataType.int_, GA.local_addr);
        getWord(); //(
        getWord();
        if (!GA.sym.equals("RPARENT")) { // )
            //error("MainFuncDef");
        }
        GA.cur_func = "main";
        getWord();
        SymTable.addLayer();
        SymTable.mainCount++;
        SymTable.voidCount = 0;
        SymTable.intCount = 0;
        this.Block = new Block();
        hasReturn = ((Block) this.Block).isHasReturn();
        if (!hasReturn) {
            GA.error("g", "expect return in main", GA.tk.getRow());
        }
        GA.add_midCode(OPType.END_FUNC, DataType.int_.toString(), "main", "DEFAULT");
        GA.local_addr = GA.LOCAL_ADDR;
    }

    @Override
    public String toString() {
        return "INTTK int\nMAINTK main\nLPARENT (\nRPARENT )\n" +
                this.Block +
                "<MainFuncDef>\n";
    }
}

class FuncFParams extends Root {
    private LinkedList<Root> FuncFParames = new LinkedList<>();

    @Override
    RootType getType() {
        return RootType.FuncFParams;
    }

    private void getWord() {
        GA.getSym();
    }

    public FuncFParams() {
        boolean isValid = true;
        FuncFParam funcFParam = new FuncFParam();
        isValid = funcFParam.isValid();
        while (GA.sym.equals("COMMA") && isValid) {
            this.FuncFParames.addLast(funcFParam);
            getWord();
            funcFParam = new FuncFParam();
            isValid = funcFParam.isValid();
        }
        //已经多读了
    }

    @Override
    public String toString() {
        if (FuncFParames.isEmpty()) {
            return "";
        }
        StringBuilder ans = new StringBuilder();
        Iterator<Root> iterator = this.FuncFParames.iterator();
        ans.append(iterator.next());
        while (iterator.hasNext()) {
            ans.append("COMMA ,\n");
            ans.append(iterator.next());
        }
        ans.append("<FuncFParams>\n");
        return ans.toString();
    }

}

class FuncFParam extends Root {
    private Token type = null;
    private Token Ident = null;
    private ConstExp ConstExp = null;
    private int dim = 0;
    private boolean isValid = true;

    @Override
    RootType getType() {
        return RootType.FuncFParam;
    }

    private void getWord() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public FuncFParam() {
        if (!GA.sym.equals("INTTK")) { // int
            isValid = false;
            return;
        }
        type = GA.tk;
        getWord(); //ident
        Ident = GA.tk;
        getWord();
        if (GA.sym.equals("LBRACK")) { // [ 一维
            getWord();
            if (!GA.sym.equals("RBRACK")) { // ]
                GA.error("k", "expect ']' in FuncFParam", GA.getPre().getRow());
                GA.retract();
            }
            getWord();
            dim++;
            if (GA.sym.equals("LBRACK")) { // [ 二维
                getWord();
                this.ConstExp = new ConstExp();
                if (!GA.sym.equals("RBRACK")) { // ]
                    GA.error("k", "expect ']' in FuncFParam", GA.getPre().getRow());
                    GA.retract();
                }
                dim++;
                SymTable.addPara(Ident, 2, GA.cur_func, GA.local_addr);
            } else { //1
                SymTable.addPara(Ident, 1, GA.cur_func, GA.local_addr);
                retract();
            }
        } else { //0
            SymTable.addPara(Ident, 0, GA.cur_func, GA.local_addr);
            retract();
        }
        getWord();
        setPara();
    }


    public void setPara() {
        if (dim == 0) {
            PseudoCodes.add(OPType.PARA, this.Ident.getToken(), "DEFAULT", "DEFAULT");
        } else if (dim == 1) {
            PseudoCodes.add(OPType.PARA, this.Ident.getToken() + "[]", "DEFAULT", "DEFAULT");
        } else {
            String dim2 = this.ConstExp.calConstExp(); //const回来肯定是个常数？
            PseudoCodes.add(OPType.PARA, this.Ident.getToken() + "[]" + "[" + dim2 + "]", "DEFAULT", "DEFAULT");
            SymTable.updateParaDims(this.Ident.getToken(), GA.cur_func, Integer.parseInt(dim2));
        }
    }

    public boolean isValid() {
        return isValid;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.type.toString());
        builder.append(this.Ident.toString());
        if (this.dim != 0) {
            builder.append("LBRACK [\nRBRACK ]\n");
            if (dim == 2) {
                builder.append("LBRACK [\n");
                builder.append(this.ConstExp);
                builder.append("RBRACK ]\n");
            }
        }
        builder.append("<FuncFParam>\n");
        return builder.toString();
    }
}

class Block extends Root {
    private LinkedList<BlockItem> items = new LinkedList<>();
    private boolean hasReturn = false;

    @Override
    RootType getType() {
        return RootType.Block;
    }

    private void getWord() {
        GA.getSym();
    }

    public Block() {
        getWord();
        while (!GA.sym.equals("RBRACE")) {
            this.items.addLast(new BlockItem());
        }
        if (!this.items.isEmpty()) {
            hasReturn = ((BlockItem) this.items.getLast()).isHasReturn();
        }
        getWord();
    }

    public Set<String> getUpdatedIdent() {
        Set<String> updatedIdents = new HashSet<>();
        for (BlockItem item : items) {
            updatedIdents.addAll(item.getUpdatedIdent());
        }
        return updatedIdents;
    }

    public boolean isHasReturn() {
        return hasReturn;
    }

    public BlockItem getLastItem() {
        return this.items.getLast();
    }

    public boolean hasItem() {
        return !this.items.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        ans.append("LBRACE {\n");
        for (Root blockItem : this.items) {
            ans.append(blockItem.toString());
        }
        ans.append("RBRACE }\n<Block>\n");
        return ans.toString();
    }
}

class BlockItem extends Root {
    private Root child = null;
    private boolean hasReturn = false;

    @Override
    RootType getType() {
        return RootType.BlockItem;
    }

    public BlockItem() {
        if (GA.sym.equals("INTTK")) { //vardecl
            this.child = new VarDecl();
        } else if (GA.sym.equals("CONSTTK")) {
            this.child = new ConstDecl();
        } else {
            this.child = new Stmt();
            hasReturn = ((Stmt) this.child).isHasReturn();
        }
    }

    public Set<String> getUpdatedIdent() {
        if (this.child instanceof Stmt) {
            return ((Stmt) this.child).getUpdatedIdent();
        } else {
            return new HashSet<>();
        }
    }

    public Stmt getStmt() {
        if (this.child instanceof Stmt) {
            return (Stmt) this.child;
        }
        System.out.println("error in BlockItem");
        return null;
    }

    public boolean isHasReturn() {
        return hasReturn;
    }

    @Override
    public String toString() {
        return this.child.toString();
    }
}

class Stmt extends Root {
    /**
     * Stmt总共有 种情况：
     * 1. LVal '=' Exp ';'
     * 2. Exp ';'
     * 3. ';'
     * 4. 'if' '(' Cond ')' Stmt
     * 5. 'if' '(' Cond ')' Stmt  'else' Stmt
     * 6. 'while' '(' Cond ')' Stmt
     * 7. 'break' ';'
     * 8. 'return' ';'
     * 9. 'return' Exp ';'
     * 10. LVal = 'getint''('')'';'
     * 11.'printf''('FormatString{,Exp}')'';'
     * 12.'continue' ';'
     * 13.Block
     */
    private int stmtType = 0;
    private Block Block = null;
    private LVal LVal = null;
    private Exp Exp = null;  // LVal = Exp, Exp; , return Exp;
    private Cond Cond = null;
    private Stmt stmt1 = null; // if-stmt, while-stmt,
    private Stmt stmt2 = null; // if-stmt-else-stmt
    private Token FormatString = null;
    private LinkedList<Exp> Exps = new LinkedList<>();//printf exps

    private boolean hasReturn = false;

    @Override
    RootType getType() {
        return RootType.Stmt;
    }

    private void getSym() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    private void skip(int record) {
        GA.skip(record);
    }

    public Stmt() {
        int record = 0;
        if (GA.sym.equals("IFTK")) { //if
            getSym();//(
            getSym();
            this.Cond = new Cond();
            //String else_label = GA.add_midCode(OPType.JUMP_IF, )
            if (!GA.sym.equals("RPARENT")) { // )
                GA.error("j", "expect ')' in Stmt if", GA.getPre().getRow());
                GA.retract();
            }
            getSym();
            //------------------中间代码---------------------------------
            String if_label = PseudoCodes.getIfLabel("IF_BODY");
            String else_label = PseudoCodes.getIfLabel("ELSE_BODY");
            String end_label = PseudoCodes.getIfLabel("END");
            this.Cond.jumpOnResult(if_label, else_label);//成功跳到if，否则为else
            GA.add_midCode(OPType.LABEL, if_label, "DEFAULT", "DEFAULT");
            this.stmt1 = new Stmt();
            if (!GA.sym.equals("ELSETK")) { //no else
                GA.add_midCode(OPType.LABEL, else_label, "DEFAULT", "DEFAULT");//对于没有else的语句，else标签就是失败跳转的位置
                stmtType = 4; //if-no else
                retract();
            } else { //else
                GA.add_midCode(OPType.GOTO, end_label, "DEFAULT", "DEFAULT");//if执行结束，直接到末尾
                GA.add_midCode(OPType.LABEL, else_label, "DEFAULT", "DEFAULT"); //这里是else的起始标签
                stmtType = 5; //if-else
                getSym();
                this.stmt2 = new Stmt();
                retract(); //TODO:ATTENTION! Retract here
                GA.add_midCode(OPType.LABEL, end_label, "DEFAULT", "DEFAULT"); //对于有else的语句，end就是end
            }
            //----------------------------------------------------------
        } else if (GA.sym.equals("WHILETK")) {
            stmtType = 6; //while
            SymTable.whileCount++;
            getSym();//(
            getSym();
            this.Cond = new Cond();
            if (!GA.sym.equals("RPARENT")) { // )
                GA.error("j", "expect ')' in Stmt while", GA.getPre().getRow());
                GA.retract();
            }
            getSym();
            //----------------------中间代码-----------------------------
            if (Compiler.isPolish) {
                polishWhileJump();
            } else {
                //非优化版本
                PseudoCodes.while_count++;
                PseudoCodes.whileStack.add(PseudoCodes.while_count);
                String start_label = PseudoCodes.getWhileLabel("START");
                String body_label = PseudoCodes.getWhileLabel("BODY");
                String end_label = PseudoCodes.getWhileLabel("END");
                GA.add_midCode(OPType.LABEL, start_label, "DEFAULT", "DEFAULT");
                this.Cond.jumpOnResult(body_label, end_label);
                GA.add_midCode(OPType.LABEL, body_label, "DEFAULT", "DEFAULT");
                this.stmt1 = new Stmt();
                GA.add_midCode(OPType.GOTO, start_label, "DEFAULT", "DEFAULT");
                GA.add_midCode(OPType.LABEL, end_label, "DEFAULT", "DEFAULT");
                retract();
                PseudoCodes.whileStack.removeLast();
                SymTable.whileCount--;
            }
            //-------------------------------------------------------------
        } else if (GA.sym.equals("BREAKTK")) {
            stmtType = 7; //break
            if (SymTable.whileCount == 0) { //not in while
                GA.error("m", "break in a non-while sentence", GA.tk.getRow());
            }
            getSym();
            if (!GA.sym.equals("SEMICN")) { //break;
                GA.error("i", "expect ';' in Stmt-break", GA.getPre().getRow());
                GA.retract();
            }
            //------------中间代码---------------
            GA.add_midCode(OPType.GOTO, "..while" + PseudoCodes.whileStack.getLast() + "..end", "DEFAULT", "DEFAULT");
            //---------------------------------
        } else if (GA.sym.equals("CONTINUETK")) {
            stmtType = 12; //continue
            if (SymTable.whileCount == 0) { //not in while
                GA.error("m", "break in a non-while sentence", GA.tk.getRow());
            }
            getSym();
            if (!GA.sym.equals("SEMICN")) { //continue;
                GA.error("i", "expect ';' in Stmt-continue", GA.getPre().getRow());
                GA.retract();
            }
            //---------------中间代码------------------
            GA.add_midCode(OPType.GOTO, "..while" + PseudoCodes.whileStack.getLast() + "..start", "DEFAULT", "DEFAULT");
            //-------------------------------------------
        } else if (GA.sym.equals("RETURNTK")) { //return
            getSym();
            Token tkPre = GA.tk;
            stmtType = 8;
            hasReturn = true; // 有return就行 不管数据流
            if (!GA.sym.equals("SEMICN")) { //有exp
                if (GA.sym.equals("RBRACE")) { //return后面直接是大括号了
                    GA.error("i", "expect ';' in Stmt-return", tkPre.getRow());
                    retract();
                } else {
                    stmtType = 9; //return exp
                    this.Exp = new Exp();
                    if (SymTable.voidCount == 1) { //in a void function
                        GA.error("f", "return exp in a void function", tkPre.getRow());
                    }
                    if (!GA.sym.equals("SEMICN")) {
                        GA.error("i", "expect ';' in Stmt-return", GA.getPre().getRow());
                        retract();
                    }
                }
            }
            //-----------中间代码------------------
            calReturn(); //如果调用了return，则添加return
            //------------------------------------
        } else if (GA.sym.equals("PRINTFTK")) { //printf
            stmtType = 11; //printf
            int expCount = 0;
            Token tkPre = GA.tk;
            getSym(); // (
            getSym();
            if (!GA.sym.equals("STRCON")) { //formatString
                //error("Stmt-printf");
            }
            this.FormatString = GA.tk;
            getSym();
            while (GA.sym.equals("COMMA")) { //{,Exp}
                getSym();
                Exps.addLast(new Exp());
                expCount++;
            }
            //check formatString
            if (expCount != SymTable.formatCount) {
                GA.error("l", "string format has " + SymTable.formatCount +
                        "'s %d, but actually has " + expCount + "'s exp", tkPre.getRow());
            }
            SymTable.formatCount = 0;
            if (!GA.sym.equals("RPARENT")) {
                GA.error("j", "expect ')' in Stmt printf", GA.getPre().getRow());
                GA.retract();
            }
            getSym();
            if (!GA.sym.equals("SEMICN")) {
                GA.error("i", "expect ';' in Stmt-printf", GA.getPre().getRow()); // ;
                retract();
            }
            //---------------------中间代码-------------------------
            String printString = this.FormatString.getToken();
            printString = printString.substring(1, printString.length() - 1); //去掉引号
            int index = 0;
            for (Exp exp : this.Exps) {
                index = PseudoCodes.pintItemAfterCal(printString, exp.calExp(), index);
            }
            PseudoCodes.pintItemAfterCal(printString, "", index); //以防最后还有一些没有输出完
            /*ArrayList<String> printNum = new ArrayList<>();
            for (Exp ep : this.Exps) { //把所有要print的数字加进去
                printNum.add(ep.calExp());
            }
            PseudoCodes.addPrintItem(printString, printNum);*/
            //-----------------------------------------------------
        } else if (GA.sym.equals("LBRACE")) { // Block
            stmtType = 13;
            SymTable.addLayer();
            this.Block = new Block();
            retract();
            SymTable.popLayer();
        } else if (GA.sym.equals("IDENFR")) { //
            record = GA.point;
            Token tkPre = GA.tk;
            Set<Integer> errorLines = new HashSet<>(Error.errors.keySet());
            //retLoc = ret.size();
            this.LVal = new LVal();
            // =
            if (!GA.sym.equals("ASSIGN")) { //TODO: Exp mis in LVal
                stmtType = 2;//exp
                skip(record);
                Error.skip(errorLines);
                this.Exp = new Exp();
                if (!GA.sym.equals("SEMICN")) {
                    GA.error("i", "expect ';' in Stmt-exp instead " + GA.tk.getToken(), GA.getPre().getRow());
                    retract();
                }
                //---------中间代码-----------
                this.Exp.calExp();
            } else { // exp is not in LVal
                SymTable.checkConst(tkPre);
                getSym();
                if (GA.sym.equals("GETINTTK")) { //LVal=getint
                    stmtType = 10;//getint
                    getSym();
                    getSym();
                    if (!GA.sym.equals("RPARENT")) { //)
                        GA.error("j", "expect ')' in Stmt-LVal-getint", GA.getPre().getRow());
                        GA.retract();
                    }
                    getSym();
                    if (!GA.sym.equals("SEMICN")) {
                        GA.error("i", "expect ';' in Stmt-LVal-getint instead " + GA.tk.getToken(), GA.getPre().getRow());
                        retract();
                    }
                    //----------------------中间代码---------------------
                    this.LVal.assignLValWithGet();
                    //-------------------------------------------------
                } else { //LVal = Exp
                    stmtType = 1;//lval=exp
                    this.Exp = new Exp();
                    if (!GA.sym.equals("SEMICN")) {
                        GA.error("i", "expect ';' in Stmt-LVal-exp instead " + GA.tk.getToken(), GA.getPre().getRow());
                        retract();
                    }
                    //----------------------中间代码---------------------
                    this.LVal.assignLVal(this.Exp.calExp());
                    //-------------------------------------------------
                }
            }
        } else { //Exp
            stmtType = 3;//exp
            if (!GA.sym.equals("SEMICN")) { //有exp
                stmtType = 2;
                this.Exp = new Exp();
                if (!GA.sym.equals("SEMICN")) {
                    GA.error("i", "expect ';' in Stmt-exp instead " + GA.tk.getToken(), GA.getPre().getRow());
                    retract();
                }
                //----------中间代码---------
                this.Exp.calExp();
            }
        }
        getSym();
    }

    public void polishWhileJump() {
        //优化版本
        PseudoCodes.while_count++;
        PseudoCodes.whileStack.add(PseudoCodes.while_count);
        String start_label = PseudoCodes.getWhileLabel("START");
        String body_label = PseudoCodes.getWhileLabel("BODY");
        String end_label = PseudoCodes.getWhileLabel("END");
        GA.add_midCode(OPType.LABEL, start_label, "DEFAULT", "DEFAULT");
        this.Cond.jumpOnResult(body_label, end_label);
        GA.add_midCode(OPType.LABEL, body_label, "DEFAULT", "DEFAULT");
        if (this.Cond.canPolish()) { // a<b, a>b, a<=b, a>=b, a==b, a!=b
            ArrayList<Set<String>> usedIdent = this.Cond.getUsedIdent();
            Set<String> left = usedIdent.get(0); //a用到的ident
            Set<String> right = usedIdent.get(1); //b用到的ident
            this.stmt1 = new Stmt();
            Set<String> updatedIdent = this.stmt1.getUpdatedIdent();
            AddOpAdd cond = this.Cond.getSimpleCond();
            boolean updateLeft = false; //updated中是否有左边用过的
            boolean updateRight = false;
            for (String token : updatedIdent) {
                for (String leftToken : left) {
                    if (leftToken.equals(token)) {
                        updateLeft = true;
                        break;
                    }
                }
                for (String rightToken : right) {
                    if (rightToken.equals(token)) {
                        updateRight = true;
                        break;
                    }
                }
            }
            String R1;
            String R2;
            if (!updateLeft) {
                R1 = cond.R1;
            } else {
                R1 = cond.add1.calAdd();
            }
            if (!updateRight) {
                R2 = cond.R2;
            } else {
                R2 = cond.add2.calAdd();
            }
            jumpToBody(R1, R2, cond.op, body_label);
        } else {
            this.stmt1 = new Stmt();
            GA.add_midCode(OPType.GOTO, start_label, "DEFAULT", "DEFAULT");
        }
        GA.add_midCode(OPType.LABEL, end_label, "DEFAULT", "DEFAULT");
        retract();
        PseudoCodes.whileStack.removeLast();
        SymTable.whileCount--;
    }

    //跳到while body中去
    public void jumpToBody(String R1, String R2, String op, String whileBody) {
        if (utlis.isInteger(R1) && utlis.isInteger(R2)) { //R1和R2都是整数
            int intR1 = Integer.parseInt(R1);
            int intR2 = Integer.parseInt(R2);
            boolean satisfy = false;
            switch (op) {
                case "LSS": //<
                    satisfy = intR1 < intR2;
                    break;
                case "LEQ":
                    satisfy = intR1 <= intR2;
                    break;
                case "GRE":
                    satisfy = intR1 > intR2;
                    break;
                case "GEQ":
                    satisfy = intR1 >= intR2;
                    break;
                case "EQL":
                    satisfy = intR1 == intR2;
                    break;
                case "NEQ":
                    satisfy = intR1!=intR2;
                    break;
            }
            if (satisfy) {
                GA.add_midCode(OPType.GOTO, whileBody, "DEFAULT", "DEFAULT");
            }
        } else {
            switch (op) {
                case "LSS": //<
                    GA.add_midCode(OPType.BLT, R1, R2, whileBody);
                    break;
                case "LEQ": //<=
                    GA.add_midCode(OPType.BLE, R1, R2, whileBody);
                    break;
                case "GRE": //>
                    GA.add_midCode(OPType.BGT, R1, R2, whileBody);
                    break;
                case "GEQ": //>=
                    GA.add_midCode(OPType.BGE, R1, R2, whileBody);
                    break;
                case "EQL": //==
                    GA.add_midCode(OPType.BEQ,R1,R2,whileBody);
                    break;
                case "NEQ": //!=
                    GA.add_midCode(OPType.BNE,R1,R2,whileBody);
                    break;
            }
        }
    }

    /**
     * 得到在此stmt中所有被改变的ident
     *
     * @return
     */
    public Set<String> getUpdatedIdent() {
        Set<String> idents = new HashSet<>();
        if (stmtType == 1 || stmtType == 10) { //lval = getint/exp
            idents.add(this.LVal.getIdent().getToken());
        } else if (stmtType == 4 || stmtType == 6) { //if -stmt, while-stmt
            idents.addAll(this.stmt1.getUpdatedIdent()); //假设不考虑全局变量
        } else if (stmtType == 5) { //if-else
            idents.addAll(this.stmt1.getUpdatedIdent());
            idents.addAll(this.stmt2.getUpdatedIdent());
        } else if (stmtType == 13) { //block
            idents.addAll(this.Block.getUpdatedIdent());
        }
        return idents;
    }

    public void calReturn() {
        if (stmtType == 9) { //return exp;
            GA.add_midCode(OPType.RETURN, this.Exp.calExp(), "DEFAULT", "DEFAULT");
        } else {
            GA.add_midCode(OPType.RETURN, "DEFAULT", "DEFAULT", "DEFAULT");
        }
    }

    public boolean isHasReturn() {
        return hasReturn;
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        if (stmtType == 8 || stmtType == 9) { //return
            ans.append("RETURNTK return\n");
        }
        if (stmtType == 4 || stmtType == 5) { //if
            ans.append("IFTK if\nLPARENT (\n");
            ans.append(this.Cond.toString());
            ans.append("RPARENT )\n");
            ans.append(this.stmt1.toString());
        }
        if (stmtType == 1 || stmtType == 10) { //LVal = ...
            ans.append(this.LVal);
            ans.append("ASSIGN =\n");
        }
        if (stmtType == 1 || stmtType == 2 || stmtType == 9) {
            ans.append(Exp);
        }
        switch (this.stmtType) {
            case 5:
                ans.append("ELSETK else\n");
                ans.append(stmt2);
                break;
            case 6:
                ans.append("WHILETK while\nLPARENT (\n");
                ans.append(this.Cond);
                ans.append("RPARENT )\n");
                ans.append(this.stmt1);
                break;
            case 7:
                ans.append("BREAKTK break\n");
                break;
            case 10:
                ans.append("GETINTTK getint\nLPARENT (\nRPARENT )\n");
                break;
            case 11:
                ans.append("PRINTFTK printf\nLPARENT (\n");
                ans.append(this.FormatString);
                for (Root expPrint : this.Exps) {
                    ans.append("COMMA ,\n");
                    ans.append(expPrint);
                }
                ans.append("RPARENT )\n");
                break;
            case 12:
                ans.append("CONTINUETK continue\n");
                break;
            case 13:
                ans.append(this.Block);
                break;
            default:
                break;
        }
        if (stmtType != 4 && stmtType != 5 && stmtType != 6 && stmtType != 13) {
            ans.append("SEMICN ;\n");
        }
        ans.append("<Stmt>\n");
        return ans.toString();
    }
}

class Exp extends Root {
    private AddExp AddExp = null;

    @Override
    RootType getType() {
        return RootType.Exp;
    }

    public Exp() {
        this.AddExp = new AddExp();
    }

    public Set<String> getUsedIdent() {
        return this.AddExp.getUsedIdent();
    }

    public String calExp() {
        return this.AddExp.calAdd();
    }

    public int getDim() {
        return ((AddExp) this.AddExp).getDim();
    }

    public String toString() {
        return this.AddExp + "<Exp>\n";
    }
}

class Cond extends Root {
    private LOrExp LOrExp = null;

    @Override
    RootType getType() {
        return RootType.Cond;
    }

    public Cond() {
        this.LOrExp = new LOrExp();
    }

    public void jumpOnResult(String successCase, String failCase) {
        this.LOrExp.jumpOnResult(successCase, failCase);
    }

    public ArrayList<Set<String>> getUsedIdent() {
        return this.LOrExp.getUsedIdent();
    }

    public AddOpAdd getSimpleCond() {
        return this.LOrExp.getSimpleCond();
    }

    public boolean canPolish() {
        return this.LOrExp.canPolish();
    }

    public String toString() {
        return this.LOrExp + "<Cond>\n";
    }
}

class LVal extends Root {
    private Token Ident = null;
    private LinkedList<Exp> Exps = new LinkedList<>();
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.LVal;
    }

    private void getSym() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public LVal() {
        int useDim = 0;
        int defDim = 0;
        this.Ident = GA.tk;
        defDim = SymTable.searchTable(Ident, 1);
        getSym();
        if (GA.sym.equals("LBRACK")) { // [
            useDim++;
            getSym();
            Exps.addLast(new Exp());
            if (!GA.sym.equals("RBRACK")) {
                GA.error("k", "expect ']' in LVal", GA.getPre().getRow());
                GA.retract();
            }
            getSym();
            if (GA.sym.equals("LBRACK")) { // [
                useDim++;
                getSym();
                Exps.addLast(new Exp());
                if (!GA.sym.equals("RBRACK")) {
                    GA.error("k", "expect ']' in LVal", GA.getPre().getRow());
                    GA.retract();
                }
            } else {
                retract();
            }
        } else { //only ident
            retract();
        }
        getSym();
        dim = defDim - useDim; //定义的减去函数实参中用到的才是真正的维数
    }

    public Token getIdent() {
        return Ident;
    }

    //作为右值才会调用这个函数
    //当维数不一致的时候，需要返回函数地址
    public String calLVal() {
        String identName = this.Ident.getToken();
        SymItem si = Objects.requireNonNull(SymTable.getSFirstNotFunc(this.Ident.getToken()));
        String siAddr = String.valueOf(si.addr);
        int lvalDim = this.Exps.size();
        int identDim = si.dim;
        if (identDim == 0) { //这个变量本来就是0维的
            String possibleValue = SymTable.searchConst(identName, 0, 0);
            if (!possibleValue.equals("WRONG")) {
                return possibleValue;
            }
            return this.Ident.getToken();
        } else if (identDim == 1) { //这个变量本身是1维的
            if (lvalDim == 1) { //需求的维数和本来的维数相等
                String dim1 = this.Exps.get(0).calExp();
                return graspValue(identName, dim1, utlis.isInteger(dim1), 1);
            } else { //需求的维数比本来的维数小，则直接返回这个数组的ident
                return this.Ident.getToken(); //例如，int func(int a[]){}; int c[3];  b=func(c);直接返回c
                /*
                if (si.cur_func.equals("GLOBAL") && si.stiType != STIType.para) { //是全局变量，返回绝对地址
                    return GA.add_midTmp(OPType.SUB, siAddr, "$sp", "AUTO", GA.cur_func);
                } else {
                    return siAddr;
                }
                 */
            }
        } else { //这个变量本来是2维的
            if (lvalDim == 2) { //调用也是2维的,就取出来
                String dim1 = this.Exps.get(0).calExp();
                String dim2 = this.Exps.get(1).calExp();
                int c = Objects.requireNonNull(SymTable.getSFirstNotFunc(identName)).dim2;
                String index = utlis.two2one(String.valueOf(c), dim1, dim2); //index = c*x+y
                return graspValue(identName, index, utlis.isInteger(index), 2);
            } else if (lvalDim == 1) { //调用是1维的，即int a[2][3], func(a[1]),返回ident名字+内部偏移量offset
                String dim1 = this.Exps.get(0).calExp(); //dim1是寻找的偏移，如上述例子里的1
                if (utlis.isInteger(dim1)) {
                    return this.Ident.getToken() + "~" + Integer.parseInt(dim1) * si.dim2 * 4;
                } else {
                    String tmp = GA.add_midTmp(OPType.MUL, String.valueOf(si.dim2 * 4), dim1, "AUTO", GA.cur_func);
                    return this.Ident.getToken() + "~" + tmp;
                }
                /*String rst;
                String dim1 = this.Exps.get(0).calExp();
                if (si.cur_func.equals("GLOBAL") && si.stiType != STIType.para) {
                    rst = GA.add_midTmp(OPType.SUB, siAddr, "$sp", "AUTO", GA.cur_func);
                } else {
                    rst = siAddr;
                }
                if (utlis.isInteger(dim1) && utlis.isInteger(rst)) {
                    rst = String.valueOf(Integer.parseInt(rst) + Integer.parseInt(dim1) * si.dim2 * 4);
                } else {
                    String tmp = GA.add_midTmp(OPType.MUL, dim1, String.valueOf(si.dim2), "AUTO", GA.cur_func);
                    GA.add_midCode(OPType.MUL, tmp, "4", tmp);
                    GA.add_midCode(OPType.ADD, tmp, rst, tmp);
                    rst = tmp;
                }
                return rst;
                 */
            } else { //调用是0维的，即int a[2][3], func(a)
                return this.Ident.getToken();
                /*
                if (si.cur_func.equals("GLOBAL") && si.stiType != STIType.para) {
                    return GA.add_midTmp(OPType.SUB, siAddr, "$sp", "AUTO", GA.cur_func);
                } else {
                    return siAddr;
                }
                 */
            }
        }
    }

    private String graspValue(String identName, String index, boolean dimIsInteger, int dim) {
        if (dimIsInteger) {
            String possibleValue = SymTable.searchConst(identName, Integer.parseInt(index), dim);
            if (!possibleValue.equals("WRONG")) {
                return possibleValue;
            }
        }
        return GA.add_midTmp(OPType.ARR_LOAD, identName, String.valueOf(index), "AUTO_ARR_LOAD", GA.cur_func);
    }

    //区别：如果是assign，就不去找具体的数是多少了
    public void assignLVal(String value) {
        SymItem si = SymTable.getSFirstNotFunc(this.Ident.getToken());
        if (this.Exps.size() == 0) {
            GA.add_midCode(OPType.ASSIGN, this.Ident.getToken(), value, "DEFAULT");
        } else {
            String dimTotal = calculateDimTotal(si);
            GA.add_midCode(OPType.ARR_SAVE, this.Ident.getToken(), dimTotal, value);
        }
    }

    private String calculateDimTotal(SymItem si) {
        //计算数组大小，尚未*4
        String dim1 = this.Exps.get(0).calExp();
        String dimTotal = dim1;
        if (this.Exps.size() == 2) {
            String dim2 = this.Exps.get(1).calExp();
            int c = si.dim2;
            dimTotal = utlis.two2one(String.valueOf(c), dim1, dim2); //dimTotal = c*x+y
        }
        return dimTotal;
    }

    public void assignLValWithGet() {
        SymItem si = SymTable.getSFirstNotFunc(this.Ident.getToken());
        if (this.Exps.size() == 0) {
            GA.add_midCode(OPType.GET_INT, this.Ident.getToken(), "DEFAULT", "DEFAULT");
        } else {
            String dimTotal = calculateDimTotal(si);
            GA.add_midCode(OPType.GET_INT, this.Ident.getToken(), dimTotal, "DEFAULT");
        }
    }


    public int getDim() {
        return dim;
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        ans.append(this.Ident.toString());
        if (Exps.size() != 0) {
            ans.append("LBRACK [\n");
            ans.append(Exps.get(0).toString());
            ans.append("RBRACK ]\n");
            if (Exps.size() == 2) {
                ans.append("LBRACK [\n");
                ans.append(Exps.get(1).toString());
                ans.append("RBRACK ]\n");
            }
        }
        ans.append("<LVal>\n");
        return ans.toString();
    }
}

class PrimaryExp extends Root {
    private Exp Exp = null;
    private LVal LVal = null;
    private Number Number = null;
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.PrimaryExp;
    }

    private void getSym() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public PrimaryExp() {
        if (GA.sym.equals("INTCON")) {//Number
            this.Number = new Number();
            retract();
        } else if (GA.sym.equals("LPARENT")) { // ( exp )
            getSym();
            this.Exp = new Exp();
            dim = ((Exp) this.Exp).getDim();
            if (!GA.sym.equals("RPARENT")) {
                //error("PrimaryExp");
            }
        } else { //LVal
            this.LVal = new LVal();
            dim = ((LVal) this.LVal).getDim();
            retract();
        }
        getSym();
    }

    /**
     * exp -> return exp's used ident
     * lval -> return ident
     * number -> null
     *
     * @return
     */
    public Set<String> getUsedIdent() {
        Set<String> idents = new HashSet<>();
        if (this.Exp != null) {
            return this.Exp.getUsedIdent();
        } else if (this.LVal != null) {
            idents.add(this.LVal.getIdent().getToken());
            return idents;
        } else { //number
            return new HashSet<>();
        }
    }

    public String calPrimary() {
        if (this.Exp != null) {
            return this.Exp.calExp();
        } else if (this.LVal != null) {
            return this.LVal.calLVal();
        } else {
            return this.Number.calNum();
        }
    }

    public int getDim() {
        return dim;
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        if (this.Exp != null) {
            ans.append("LPARENT (\n");
            ans.append(this.Exp.toString());
            ans.append("RPARENT )\n");
        } else if (this.LVal != null) {
            ans.append(this.LVal.toString());
        } else {
            ans.append(this.Number.toString());
        }
        ans.append("<PrimaryExp>\n");
        return ans.toString();
    }
}

class Number extends Root {
    private Token IntConst = null;

    @Override
    RootType getType() {
        return RootType.Number;
    }

    public Number() {
        this.IntConst = GA.tk;
        GA.getSym();
    }

    public String calNum() {
        return this.IntConst.getToken();
    }

    public String toString() {
        return this.IntConst + "<Number>\n";
    }
}


class UnaryExp extends Root {
    private PrimaryExp PrimaryExp = null;
    private Token Ident = null;
    private FuncRParams FuncRParams = null;
    private UnaryOp UnaryOp = null;
    private UnaryExp OutUnaryExp = null;
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.UnaryExp;
    }

    private void getSym() {
        GA.getSym();
    }

    private void retract() {
        GA.retract();
    }

    public UnaryExp() {
        if (GA.sym.equals("IDENFR")) { //ident
            this.Ident = GA.tk;
            getSym(); //(
            if (!GA.sym.equals("LPARENT")) { //是primaryExp
                retract();
                this.PrimaryExp = new PrimaryExp();
                dim = ((PrimaryExp) this.PrimaryExp).getDim();
                retract();
            } else { //是函数调用
                ArrayList<Integer> dims = new ArrayList<>(); //dims为实参列表的参数[实际上是defDim-useDim]
                dim = SymTable.searchTable(Ident, 2); //void为-1，int为0
                getSym(); // )
                if (!GA.sym.equals("RPARENT")) { //有FuncRParams
                    if (GA.sym.equals("IDENFR") || GA.sym.equals("INTCON") || GA.sym.equals("LPARENT") ||
                            GA.sym.equals("PLUS") || GA.sym.equals("MINU") || GA.sym.equals("NOT")) {
                        //说明是FuncRParams,这个判断是为了防止c[f(]的情况
                        this.FuncRParams = new FuncRParams();
                        dims = ((FuncRParams) this.FuncRParams).getDims();
                    }
                    if (!GA.sym.equals("RPARENT")) {
                        GA.error("j", "expect ')' in UnaryExp ", GA.getPre().getRow()); //)
                        retract();
                    }
                }
                SymTable.checkRParam(this.Ident, dims);
            }
        } else if (GA.sym.equals("PLUS") || GA.sym.equals("MINU") || GA.sym.equals("NOT")) { //Op
            this.UnaryOp = new UnaryOp();
            this.OutUnaryExp = new UnaryExp();
            retract();
        } else {
            this.PrimaryExp = new PrimaryExp();
            dim = (this.PrimaryExp).getDim();
            retract();
        }
        getSym();
    }

    public Set<String> getUsedIdent() {
        if (this.PrimaryExp != null) {
            return this.PrimaryExp.getUsedIdent();
        } else if (this.OutUnaryExp != null) {
            return this.OutUnaryExp.getUsedIdent();
        } else { //函数调用默认为空
            return new HashSet<>();
        }
    }

    public String calUnary() {
        if (this.PrimaryExp != null) {
            return this.PrimaryExp.calPrimary();
        } else if (this.Ident != null) {
            //PRE_CALL
            GA.add_midCode(OPType.PRE_CALL, this.Ident.getToken(), "DEFAULT", "DEFAULT");
            SymItem func = SymTable.getFunc(this.Ident.getToken());
            String rst;
            //PUSH
            if (this.FuncRParams != null) {
                this.FuncRParams.pushFuncRParams();
            }
            /*
            String rparams = this.FuncRParams.pushFuncRParams(); //把实参都push进去
            //减少栈指针
            int delta = -GA.local_addr + this.FuncRParams.getExps().size() * 4 + 128;
            GA.add_midCode(OPType.SUB, "$sp", String.valueOf(delta), "$sp");
            //装参数
            String[] params = rparams.split(" ");
            for (int i = 0; i < params.length; i++) {
                rst = params[i];
                if (func.paras.get(i).dim > 0) { //传数组
                    if (utlis.isInteger(rst)) {
                        rst = String.valueOf(delta + Integer.parseInt(rst));
                    } else {
                        rst = GA.add_midTmp(OPType.ADD, rst, String.valueOf(delta), "AUTO", GA.cur_func);
                    }
                }
                GA.add_midCode(OPType.PUSH, rst, "DEFAULT", "DEFAULT");
            }
             */
            //CALL
            GA.add_midCode(OPType.CALL, this.Ident.getToken(), "DEFAULT", "DEFAULT");
            //recover
            //GA.add_midCode(OPType.ADD, "$sp", String.valueOf(delta), "$sp");
            if (func.dataType == DataType.int_) {
                rst = GA.add_midTmp(OPType.GET_RET, "DEFAULT", "DEFAULT", "AUTO_RET", GA.cur_func);
            } else {
                rst = "0";
            }
            return rst;
        } else {
            String op = this.UnaryOp.getStringOp();
            String num = this.OutUnaryExp.calUnary();
            if (!op.equals("!") && utlis.isInteger(num)) {
                return String.valueOf(utlis.calInteger("0", num, op));
            }
            if (op.equals("+")) {
                return GA.add_midTmp(OPType.ADD, "0", this.OutUnaryExp.calUnary(), "AUTO", GA.cur_func);
            } else if (op.equals("-")) {
                return GA.add_midTmp(OPType.SUB, "0", this.OutUnaryExp.calUnary(), "AUTO", GA.cur_func);
            } else {
                if (utlis.isInteger(num)) {
                    if (Integer.parseInt(num) == 0) { //!0 就是 1
                        return "1";
                    } else {
                        return "0";
                    }
                } else { //不是数字，就需要比较一下是不是0
                    return PseudoCodes.setNotAdd(OPType.SEQ, "0", num, "AUTO_SEQ", GA.cur_func);
                }
            }
        }
    }

    public int getDim() {
        return dim;
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        if (this.PrimaryExp != null) {
            ans.append(this.PrimaryExp.toString());
        } else if (Ident != null) {
            ans.append(this.Ident.toString());
            ans.append("LPARENT (\n");
            if (this.FuncRParams != null) {
                ans.append(this.FuncRParams.toString());
            }
            ans.append("RPARENT )\n");
        } else {
            ans.append(this.UnaryOp.toString());
            ans.append(this.OutUnaryExp.toString());
        }
        ans.append("<UnaryExp>\n");
        return ans.toString();
    }
}

class UnaryOp extends Root {
    private Token symbol = null;

    @Override
    RootType getType() {
        return RootType.UnaryOp;
    }

    public UnaryOp() {
        this.symbol = GA.tk;
        GA.getSym();
    }

    public String getStringOp() {
        return this.symbol.getToken();
    }

    @Override
    public String toString() {
        return this.symbol.toString() + "<UnaryOp>\n";
    }
}

class FuncRParams extends Root {
    private LinkedList<Exp> Exps = new LinkedList<>();
    private ArrayList<Integer> dims = new ArrayList<>();

    @Override
    RootType getType() {
        return RootType.FuncRParams;
    }

    private void getSym() {
        GA.getSym();
    }

    public FuncRParams() {
        this.Exps.addLast(new Exp());
        dims.add(((Exp) Exps.getLast()).getDim());
        while (GA.sym.equals("COMMA")) {
            getSym();
            this.Exps.addLast(new Exp());
            dims.add(((Exp) Exps.getLast()).getDim());
        }
    }

    public void pushFuncRParams() {
        /*
        StringBuilder builder = new StringBuilder();//把所有结果按照参数从左到右排列，空格分隔
        for (Exp exp : this.Exps) {
            builder.append(exp.calExp());
            builder.append(" ");
        }
        return builder.toString();
        */
        for (Exp exp : this.Exps) {
            GA.add_midCode(OPType.PUSH, exp.calExp(), "DEFAULT", "DEFAULT");
        }
    }

    public LinkedList<Exp> getExps() {
        return Exps;
    }

    public ArrayList<Integer> getDims() {
        return dims;
    }

    @Override
    public String toString() {
        StringBuilder ans = new StringBuilder();
        ans.append(this.Exps.get(0).toString());
        for (int i = 1; i < Exps.size(); i++) {
            ans.append("COMMA ,\n");
            ans.append(this.Exps.get(i).toString());
        }
        ans.append("<FuncRParams>\n");
        return ans.toString();
    }
}

class MulExp extends Root {
    private LinkedList<UnaryExp> UnaryExps = new LinkedList<>();
    private LinkedList<Token> ops = new LinkedList<>();
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.MulExp;
    }

    private void getSym() {
        GA.getSym();
    }

    public MulExp() {
        this.UnaryExps.addLast(new UnaryExp());
        dim = ((UnaryExp) UnaryExps.getLast()).getDim();
        while (GA.sym.equals("MULT") || GA.sym.equals("DIV") || GA.sym.equals("MOD")) {
            this.ops.addLast(GA.tk);
            getSym();
            this.UnaryExps.addLast(new UnaryExp());
            dim = 0; //如果出现*/%，则为0纬
        }
    }

    public Set<String> getUsedIdent() {
        Set<String> idents = new HashSet<>();
        for (UnaryExp ue : UnaryExps) {
            idents.addAll(ue.getUsedIdent());
        }
        return idents;
    }

    public String calMul() {
        String num1 = this.UnaryExps.get(0).calUnary();
        String num2;
        int size = this.UnaryExps.size();
        String op;
        for (int i = 1; i < size; i++) {
            op = this.ops.get(i - 1).getToken();
            num2 = this.UnaryExps.get(i).calUnary();
            if (utlis.isInteger(num1) && utlis.isInteger(num2)) {
                num1 = String.valueOf(utlis.calInteger(num1, num2, op));
                continue;
            }
            if (op.equals("*")) {
                num1 = GA.add_midTmp(OPType.MUL, num1, num2, "AUTO", GA.cur_func);
            } else if (op.equals("/")) {
                num1 = GA.add_midTmp(OPType.DIV, num1, num2, "AUTO", GA.cur_func);
            } else { //"%"
                num1 = GA.add_midTmp(OPType.MOL, num1, num2, "AUTO", GA.cur_func);
            }
        }
        return num1;
    }

    public int getDim() {
        return dim;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<UnaryExp> unaryIter = this.UnaryExps.iterator();
        Iterator<Token> opIter = this.ops.iterator();
        builder.append(unaryIter.next());
        builder.append("<MulExp>\n");
        while (unaryIter.hasNext()) {
            builder.append(opIter.next());
            builder.append(unaryIter.next());
            builder.append("<MulExp>\n");
        }
        return builder.toString();
    }
}

class AddExp extends Root {
    private LinkedList<MulExp> MulExps = new LinkedList<>();
    private LinkedList<Token> ops = new LinkedList<>();
    private int dim = 0;

    @Override
    RootType getType() {
        return RootType.AddExp;
    }

    private void getSym() {
        GA.getSym();
    }

    public AddExp() {
        MulExps.addLast(new MulExp());
        dim = (MulExps.getLast()).getDim();
        while (GA.sym.equals("PLUS") || GA.sym.equals("MINU")) {
            this.ops.addLast(GA.tk);
            getSym();
            MulExps.addLast(new MulExp());
            dim = 0;
        }
    }

    public Set<String> getUsedIdent() {
        Set<String> idents = new HashSet<>();
        for (MulExp mul : MulExps) {
            idents.addAll(mul.getUsedIdent());
        }
        return idents;
    }

    /**
     * 优化思路：
     * 把所有常数先进行加减组合为一个常数，再让它和别的变量相加/减
     * 优化 d = 2 + a + 3 + b - 6 + c; ——> d = a + b + c + (-1)
     * 方法：建立OpAndExp类，存放每一个exp和它前面的op，第一个数默认为+
     * 成立前提：加减运算是可交换的
     */
    public String calAdd() {
        if (!Compiler.isPolish) {
            return calAddWithoutPolish();
        }
        //下面开启优化
        int start = 0;
        int size = this.MulExps.size();
        int constant = 0;
        String num1 = null;
        for (int i = 0; i < size; i++) {
            String num = this.MulExps.get(i).calMul();
            if (!utlis.isInteger(num)) {
                if (i == 0) { //第一个就是表达式
                    num1 = num;
                } else if (ops.get(i - 1).getToken().equals("+")) {
                    num1 = num;
                } else {
                    num1 = GA.add_midTmp(OPType.SUB, "0", num, "AUTO", GA.cur_func);
                }
                start = i + 1;
                break;
            }
            if (i == 0) { //第一个就是个数字
                constant += Integer.parseInt(num);
            } else if (ops.get(i - 1).getToken().equals("+")) {
                constant += Integer.parseInt(num);
            } else {
                constant -= Integer.parseInt(num);
            }
        }
        if (num1 == null) { //说明整个式子中都没有表达式
            return String.valueOf(constant);
        }
        String num2;
        String op;
        for (int i = start; i < size; i++) {
            num2 = this.MulExps.get(i).calMul();
            op = this.ops.get(i - 1).getToken();
            if (utlis.isInteger(num2)) {
                if (op.equals("+")) {
                    constant += Integer.parseInt(num2);
                } else {
                    constant -= Integer.parseInt(num2);
                }
            } else {
                if (op.equals("+")) {
                    num1 = GA.add_midTmp(OPType.ADD, num1, num2, "AUTO", GA.cur_func);
                } else {
                    num1 = GA.add_midTmp(OPType.SUB, num1, num2, "AUTO", GA.cur_func);
                }
            }
        }
        if (constant != 0) {
            num1 = GA.add_midTmp(OPType.ADD, num1, String.valueOf(constant), "AUTO", GA.cur_func);
        }
        return num1;
    }

    public String calAddWithoutPolish() {
        String num1 = this.MulExps.get(0).calMul();
        String num2;
        int size = this.MulExps.size();
        String op;
        for (int i = 1; i < size; i++) {
            op = this.ops.get(i - 1).getToken();
            num2 = this.MulExps.get(i).calMul();
            if (utlis.isInteger(num1) && utlis.isInteger(num2)) {
                num1 = String.valueOf(utlis.calInteger(num1, num2, op));
                continue;
            }
            if (op.equals("+")) {
                num1 = GA.add_midTmp(OPType.ADD, num1, num2, "AUTO", GA.cur_func);
            } else { //"-"
                num1 = GA.add_midTmp(OPType.SUB, num1, num2, "AUTO", GA.cur_func);
            }
        }
        return num1;
    }


    public int getDim() {
        return dim;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<MulExp> mulIter = this.MulExps.iterator();
        Iterator<Token> opIter = this.ops.iterator();
        builder.append(mulIter.next());
        builder.append("<AddExp>\n");
        while (mulIter.hasNext()) {
            builder.append(opIter.next());
            builder.append(mulIter.next());
            builder.append("<AddExp>\n");
        }
        return builder.toString();
    }
}

class RelExp extends Root {
    private LinkedList<AddExp> AddExps = new LinkedList<>();
    private LinkedList<Token> ops = new LinkedList<>();
    private String R1;//只有两个Addexp才会用
    private String R2;

    @Override
    RootType getType() {
        return RootType.RelExp;
    }

    private void getSym() {
        GA.getSym();
    }

    public RelExp() {
        this.AddExps.addLast(new AddExp());
        while (GA.sym.equals("LSS") || GA.sym.equals("GRE") || GA.sym.equals("LEQ") || GA.sym.equals("GEQ")) {
            this.ops.addLast(GA.tk);
            getSym();
            this.AddExps.addLast(new AddExp());
        }
    }

    public boolean canPolish() {
        if (this.AddExps.size() == 2) {
            return true;
        }
        return false;
    }

    //只有有两个addexp时才用到
    public ArrayList<Set<String>> getUsedIdent() {
        Set<String> add1 = this.AddExps.get(0).getUsedIdent();
        Set<String> add2 = this.AddExps.get(1).getUsedIdent();
        ArrayList<Set<String>> ret = new ArrayList<>();
        ret.add(add1);
        ret.add(add2);
        return ret;
    }

    //只有两个addexp时才用到
    public AddOpAdd getSimpleCond() {
        return new AddOpAdd(this.AddExps.get(0), this.AddExps.get(1), R1, R2, this.ops.get(0).getSymbol());
    }

    /**
     * 只有两个addexp时才会调用
     */
    //todo 欠查询
    public void jumpOnResultOne(String successCase, String failCase) {
        R1 = this.AddExps.get(0).calAdd();
        R2 = this.AddExps.get(1).calAdd();
        String op = this.ops.get(0).getSymbol();
        if (utlis.isInteger(R1) && utlis.isInteger(R2)) { //R1和R2都是整数
            int intR1 = Integer.parseInt(R1);
            int intR2 = Integer.parseInt(R2);
            boolean satisfy = false;
            switch (op) {
                case "LSS": //<
                    satisfy = intR1 < intR2;
                    break;
                case "LEQ":
                    satisfy = intR1 <= intR2;
                    break;
                case "GRE":
                    satisfy = intR1 > intR2;
                    break;
                case "GEQ":
                    satisfy = intR1 >= intR2;
                    break;
            }
            if (satisfy && successCase != null) {
                GA.add_midCode(OPType.GOTO, successCase, "DEFAULT", "DEFAULT");
            } else if (!satisfy && failCase != null) {
                GA.add_midCode(OPType.GOTO, failCase, "DEFAULT", "DEFAULT");
            }
        } else {
            switch (op) {
                case "LSS": //<
                    if (successCase != null) { //成功时转移
                        GA.add_midCode(OPType.BLT, R1, R2, successCase);
                    } else if (failCase != null) { //不成功转移，则为branch greater equal
                        GA.add_midCode(OPType.BGE, R1, R2, failCase);
                    }
                    break;
                case "LEQ": //<=
                    if (successCase != null) {
                        GA.add_midCode(OPType.BLE, R1, R2, successCase);
                    } else if (failCase != null) {
                        GA.add_midCode(OPType.BGT, R1, R2, failCase);
                    }
                    break;
                case "GRE": //>
                    if (successCase != null) {
                        GA.add_midCode(OPType.BGT, R1, R2, successCase);
                    } else if (failCase != null) {
                        GA.add_midCode(OPType.BLE, R1, R2, failCase);
                    }
                    break;
                case "GEQ": //>=
                    if (successCase != null) {
                        GA.add_midCode(OPType.BGE, R1, R2, successCase);
                    } else if (failCase != null) {
                        GA.add_midCode(OPType.BLT, R1, R2, failCase);
                    }
                    break;
            }
        }
    }

    public String calculate() {
        String t1 = this.AddExps.getFirst().calAdd();
        String t2;
        Token op;
        for (int i = 1; i < this.AddExps.size(); i++) {
            t2 = this.AddExps.get(i).calAdd();
            op = this.ops.get(i - 1);
            if (utlis.isInteger(t1) && utlis.isInteger(t2)) {
                int intT1 = Integer.parseInt(t1);
                int intT2 = Integer.parseInt(t2);
                switch (op.getSymbol()) {
                    case "LSS": //<
                        t1 = intT1 < intT2 ? "1" : "0";
                        break;
                    case "LEQ": //<=
                        t1 = intT1 <= intT2 ? "1" : "0";
                        break;
                    case "GRE":
                        t1 = intT1 > intT2 ? "1" : "0";
                        break;
                    case "GEQ":
                        t1 = intT1 >= intT2 ? "1" : "0";
                        break;
                }
            } else {
                String tmp = null;
                switch (op.getSymbol()) {
                    case "LSS": //<
                        tmp = GA.add_midTmp(OPType.SLT, t1, t2, "AUTO_SLT", GA.cur_func);
                        break;
                    case "LEQ": //<=
                        tmp = GA.add_midTmp(OPType.SLE, t1, t2, "AUTO_SLE", GA.cur_func);
                        break;
                    case "GRE":
                        tmp = GA.add_midTmp(OPType.SLT, t2, t1, "AUTO_SLT", GA.cur_func);
                        break;
                    case "GEQ":
                        tmp = GA.add_midTmp(OPType.SLE, t2, t1, "AUTO_SLE", GA.cur_func);
                        break;
                    default:
                        System.out.println("in RelExp, switch-case out of index");
                }
                t1 = tmp;
            }
        }
        return t1;
    }

    public LinkedList<AddExp> getAddExps() {
        return AddExps;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<AddExp> addIter = this.AddExps.iterator();
        Iterator<Token> opIter = this.ops.iterator();
        builder.append(addIter.next());
        builder.append("<RelExp>\n");
        while (addIter.hasNext()) {
            builder.append(opIter.next());
            builder.append(addIter.next());
            builder.append("<RelExp>\n");
        }
        return builder.toString();
    }
}

class EqExp extends Root {
    private LinkedList<RelExp> RelExps = new LinkedList<>();
    private LinkedList<Token> ops = new LinkedList<>();
    private String R1;
    private String R2;

    @Override
    RootType getType() {
        return RootType.EqExp;
    }

    private void getSym() {
        GA.getSym();
    }

    public EqExp() {
        this.RelExps.addLast(new RelExp());
        while (GA.sym.equals("EQL") || GA.sym.equals("NEQ")) {
            this.ops.addLast(GA.tk);
            getSym();
            this.RelExps.addLast(new RelExp());
        }
    }

    public boolean canPolish() {
        //做了 A==B的跳转优化
        //为A==B 且A和B中各都只有一个addexp时
        if (this.RelExps.size() == 2 &&
                this.RelExps.get(0).getAddExps().size() == 1 &&
                this.RelExps.get(1).getAddExps().size() == 1) {
            return true;
        }
        if (this.RelExps.size() > 1) {
            return false;
        }
        return this.RelExps.get(0).canPolish();
    }

    public AddOpAdd getSimpleCond() {
        if (this.RelExps.size() == 2 &&
                this.RelExps.get(0).getAddExps().size() == 1 &&
                this.RelExps.get(1).getAddExps().size() == 1) {
            return new AddOpAdd(this.RelExps.get(0).getAddExps().get(0),
                    this.RelExps.get(1).getAddExps().get(0), R1, R2,
                    this.ops.get(0).getSymbol());
        }
        return this.RelExps.get(0).getSimpleCond();
    }

    public ArrayList<Set<String>> getUsedIdent() {
        if(this.RelExps.size() == 2 &&
                this.RelExps.get(0).getAddExps().size() == 1 &&
                this.RelExps.get(1).getAddExps().size() == 1){
            ArrayList<Set<String>> usedIdent = new ArrayList<>();
            usedIdent.add(this.RelExps.get(0).getAddExps().get(0).getUsedIdent());
            usedIdent.add(this.RelExps.get(1).getAddExps().get(0).getUsedIdent());
            return usedIdent;
        }
        return this.RelExps.get(0).getUsedIdent();
    }

    public LinkedList<RelExp> getRelExps() {
        return RelExps;
    }

    public String calculate() {
        String ans = this.RelExps.getFirst().calculate();
        boolean isInteger = utlis.isInteger(ans);
        int intAns = 0;
        if (isInteger) {
            intAns = Integer.parseInt(ans);
        }
        String t1;
        for (int i = 1; i < this.RelExps.size(); i++) {
            t1 = this.RelExps.get(i).calculate();
            if (ops.get(i - 1).getSymbol().equals("EQL")) { // A == B
                if (isInteger && utlis.isInteger(t1)) { //前一个数和后一个数都是整型
                    if (intAns == Integer.parseInt(t1)) {
                        intAns = 1;
                        ans = "1";
                    } else {
                        intAns = 0;
                        ans = "0";
                    }
                } else {
                    isInteger = false;
                    String t2 = PseudoCodes.setNotAdd(OPType.SEQ, ans, t1, "AUTO_SEQ", GA.cur_func);
                    ans = t2;
                }
            } else { //A!=B
                if (isInteger && utlis.isInteger(t1)) {
                    if (intAns != Integer.parseInt(t1)) {
                        intAns = 1;
                        ans = "1";
                    } else {
                        intAns = 0;
                        ans = "0";
                    }
                } else {
                    isInteger = false;
                    String t2 = GA.add_midTmp(OPType.SNE, ans, t1, "AUTO_SNE", GA.cur_func);
                    ans = t2;
                }
            }
        }
        return ans;
    }

    /**
     * Only RelExp只有一个且里面的AddExp只有两个的时候才会调用 ——> a>b
     */
    public void jumpOnResultOne(String successCase, String failCase) {
        this.RelExps.get(0).jumpOnResultOne(successCase, failCase);
    }

    /**
     * Only A==B or A!=B时才会调用
     */
    public void jumpOnResult(String successCase, String failCase) {
        R1 = this.RelExps.get(0).calculate();
        R2 = this.RelExps.get(1).calculate();
        String op = this.ops.get(0).getSymbol();
        if (utlis.isInteger(R1) && utlis.isInteger(R2)) {
            boolean judge = (Integer.parseInt(R1) == Integer.parseInt(R2));
            if (op.equals("NEQ")) {
                judge = !judge;
            }
            if (judge && (successCase != null)) {
                GA.add_midCode(OPType.GOTO, successCase, "DEFAULT", "DEFAULT");
            } else if (!judge && (failCase != null)) {
                GA.add_midCode(OPType.GOTO, failCase, "DEFAULT", "DEFAULT");
            }
        } else {
            if (op.equals("EQL")) { // ==
                //以下两个应该不会同时为null，也不会同时不为null
                if (successCase != null) {
                    GA.add_midCode(OPType.BEQ, R1, R2, successCase);
                }
                if (failCase != null) {
                    GA.add_midCode(OPType.BNE, R1, R2, failCase);
                }
            } else {
                if (successCase != null) {
                    GA.add_midCode(OPType.BNE, R1, R2, successCase);
                }
                if (failCase != null) {
                    GA.add_midCode(OPType.BEQ, R1, R2, failCase);
                }
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<RelExp> relIter = this.RelExps.iterator();
        Iterator<Token> opIter = this.ops.iterator();
        builder.append(relIter.next());
        builder.append("<EqExp>\n");
        while (relIter.hasNext()) {
            builder.append(opIter.next());
            builder.append(relIter.next());
            builder.append("<EqExp>\n");
        }
        return builder.toString();
    }
}

class LAndExp extends Root {
    private LinkedList<EqExp> EqExps = new LinkedList<>();
    private LinkedList<Token> ops = new LinkedList<>();

    @Override
    RootType getType() {
        return RootType.LAndExp;
    }

    private void getSym() {
        GA.getSym();
    }

    public LAndExp() {
        this.EqExps.addLast(new EqExp());
        while (GA.sym.equals("AND")) {
            this.ops.addLast(GA.tk);
            getSym();
            this.EqExps.addLast(new EqExp());
        }
    }

    public boolean canPolish() {
        if (this.EqExps.size() > 1) {
            return false;
        }
        return this.EqExps.get(0).canPolish();
    }

    public AddOpAdd getSimpleCond() {
        return this.EqExps.get(0).getSimpleCond();
    }

    public ArrayList<Set<String>> getUsedIdent() {
        return this.EqExps.get(0).getUsedIdent();
    }

    public void jumpOnResult(String successCase, String failCase) {
        if (this.EqExps.size() == 1) { //只有一个元素，需要特判以防跳重
            EqExp eq = EqExps.getFirst();
            if (eq.getRelExps().size() == 1 && eq.getRelExps().getFirst().getAddExps().size() == 2) {
                //只有一个RelExp，且这个RelExp里面恰好有两个addExp，说明为 a<b 这种情况
                eq.jumpOnResultOne(successCase, null);
            } else if (eq.getRelExps().size() == 2) {
                //只有当 A==B或A!=B 时调用EqExp的这个方法
                eq.jumpOnResult(successCase, null);
            } else {
                //如果唯一的eqexp不等于0，则直接成功，否则继续走下去
                String ans = eq.calculate();
                if (utlis.isInteger(ans)) {
                    int intAns = Integer.parseInt(ans);
                    if (intAns != 0) {
                        GA.add_midCode(OPType.GOTO, successCase, "DEFAULT", "DEFAULT");
                    }
                } else {
                    //不等于0时转移
                    GA.add_midCode(OPType.BNE, ans, "0", successCase);
                }
            }
        } else {
            //andEndLabel是这个and语句结束的位置，如果and中有一个eqexp为假，则直接跳转到此条and语句结束的位置
            String andEndLabel = PseudoCodes.getAndLabel();
            for (EqExp eq : EqExps) {
                if (eq.getRelExps().size() == 1 && eq.getRelExps().getFirst().getAddExps().size() == 2) {
                    eq.jumpOnResultOne(null, andEndLabel);
                } else if (eq.getRelExps().size() == 2) {
                    eq.jumpOnResult(null, andEndLabel);
                } else {
                    String ans = eq.calculate();
                    if (utlis.isInteger(ans)) {
                        int intAns = Integer.parseInt(ans);
                        if (intAns == 0) {
                            GA.add_midCode(OPType.GOTO, andEndLabel, "DEFAULT", "DEFAULT");
                        }
                    } else {
                        GA.add_midCode(OPType.BEQ, ans, "0", andEndLabel);
                    }
                }
            }
            GA.add_midCode(OPType.GOTO, successCase, "DEFAULT", "DEFAULT");
            GA.add_midCode(OPType.LABEL, andEndLabel, "DEFAULT", "DEFAULT");
        }
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<EqExp> eqIter = this.EqExps.iterator();
        Iterator<Token> opIter = this.ops.iterator();
        builder.append(eqIter.next());
        builder.append("<LAndExp>\n");
        while (eqIter.hasNext()) {
            builder.append(opIter.next());
            builder.append(eqIter.next());
            builder.append("<LAndExp>\n");
        }
        return builder.toString();
    }
}

class LOrExp extends Root {
    private LinkedList<LAndExp> LAndExps = new LinkedList<>();
    private LinkedList<Token> ops = new LinkedList<>();

    @Override
    RootType getType() {
        return RootType.LOrExp;
    }

    private void getSym() {
        GA.getSym();
    }

    public LOrExp() {
        this.LAndExps.addLast(new LAndExp());
        while (GA.sym.equals("OR")) {
            this.ops.addLast(GA.tk);
            getSym();
            this.LAndExps.addLast(new LAndExp());
        }
    }

    public AddOpAdd getSimpleCond() {
        return this.LAndExps.get(0).getSimpleCond();
    }

    public void jumpOnResult(String successCase, String failCase) {
        for (LAndExp la : LAndExps) {
            la.jumpOnResult(successCase, null);
        }
        GA.add_midCode(OPType.GOTO, failCase, "DEFAULT", "DEFAULT");
    }

    public ArrayList<Set<String>> getUsedIdent() {
        return this.LAndExps.get(0).getUsedIdent();
    }

    public boolean canPolish() {
        if (this.LAndExps.size() > 1) {
            return false;
        }
        return this.LAndExps.get(0).canPolish();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<LAndExp> landIter = this.LAndExps.iterator();
        Iterator<Token> opIter = this.ops.iterator();
        builder.append(landIter.next());
        builder.append("<LOrExp>\n");
        while (landIter.hasNext()) {
            builder.append(opIter.next());
            builder.append(landIter.next());
            builder.append("<LOrExp>\n");
        }
        return builder.toString();
    }
}

class ConstExp extends Root {
    private AddExp AddExp = null;

    @Override
    RootType getType() {
        return RootType.ConstExp;
    }

    public ConstExp() {
        this.AddExp = new AddExp();
    }

    public String calConstExp() {
        return this.AddExp.calAdd();
    }

    @Override
    public String toString() {
        return this.AddExp + "<ConstExp>\n";
    }
}
