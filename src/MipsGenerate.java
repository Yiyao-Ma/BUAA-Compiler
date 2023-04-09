import java.util.*;

public class MipsGenerate {
    private boolean isPolish = Compiler.isPolish; //优化开关
    private ArrayList<PseudoCode> midCodes = PseudoCodes.midCodes;
    private ArrayList<String> mips = new ArrayList<>();
    private String s_reg[] = {"DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT",
            "DEFAULT", "DEFAULT", "DEFAULT"}; //8个寄存器
    private String t_reg[] = {"DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT",
            "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT"}; //10个寄存器
    private String cur_func = "GLOBAL";
    private LinkedList<Integer> func_sp_offset = new LinkedList<>(); //函数开辟的栈的大小，是一个栈
    private LinkedList<Integer> paraNums = new LinkedList<>();
    private int cur_line = 0;
    private int divLabel = 0; //除法优化使用的标签
    private int molLabel = 0; //模优化使用的标签
    public static final HashMap<Integer, Integer> div2shift = new HashMap<Integer, Integer>() {{
        put(-5, 1);
        put(-3, 1);
        put(3, 0);
        put(5, 1);
        put(6, 0);
        put(7, 2);
        put(9, 1);
        put(10, 2);
        put(11, 1);
        put(12, 1);
        put(25, 3);
        put(125, 3);
        put(625, 8);
    }};
    public static final HashMap<Integer, Integer> div2mag = new HashMap<Integer, Integer>() {{
        put(-5, 0x99999999);
        put(-3, 0x55555555);
        put(3, 0x55555556);
        put(5, 0x66666667);
        put(6, 0x2AAAAAAB);
        put(7, 0x92492493);
        put(9, 0x38E38E39);
        put(10, 0x66666667);
        put(11, 0x2E8BA2E9);
        put(12, 0x2AAAAAAB);
        put(25, 0x51EB851F);
        put(125, 0x10624DD3);
        put(625, 0x68DB8BAD);
    }};
    private Map<OPType, String> op2instr = new TreeMap<OPType, String>() {
        {
            put(OPType.ADD, "addu");
            put(OPType.SUB, "subu");
            put(OPType.MUL, "mul");
            put(OPType.DIV, "div");
            put(OPType.MOL, "mol");
        }
    };
    //下面这个map是用来当两个操作数位置转换时，同步变化的
    private Map<String, String> branchTransfer = new TreeMap<String, String>() {
        {
            put("beq", "beq");
            put("bne", "bne");
            put("blt", "bgt"); // a<b -> b>a
            put("bgt", "blt"); // a>b -> b<a
            put("bge", "ble"); // a>=b -> b<=a
            put("ble", "bge"); // a<=b -> b>=a
        }
    };

    private Map<OPType, String> set2instr = new TreeMap<OPType, String>() {
        {
            put(OPType.SLE, "sle");
            put(OPType.SLT, "slt");
            put(OPType.SNE, "sne");
            put(OPType.SEQ, "seq");
        }
    };

    private Map<OPType, String> branch2instr = new TreeMap<OPType, String>() {
        {
            put(OPType.BEQ, "beq");
            put(OPType.BNE, "bne");
            put(OPType.BLT, "blt");
            put(OPType.BLE, "ble");
            put(OPType.BGT, "bgt");
            put(OPType.BGE, "bge");
        }
    };

    public MipsGenerate() {
        mid2mip();
    }

    public ArrayList<String> getMips() {
        return mips;
    }

    public void generate(String mipsCode) {
        mips.add(mipsCode);
    }

    public void generate(String op, String num) {
        generate(op + " " + num);
    }

    public void generate(String op, String num1, String num2) {
        generate(op + " " + num1 + ", " + num2);
        release(num2);
        if (op.equals("sw") || op.equals("bltz") || op.equals("blez")
                || op.equals("bgtz") || op.equals("bgez")) {
            release(num1);
        }
    }

    public void generate(String op, String num1, String num2, String num3) {
        generate(op + " " + num1 + ", " + num2 + ", " + num3);
        if (op.equals("addu") || op.equals("subu") || op.equals("mul") ||
                op.equals("div") || op.equals("sll") || op.equals("sra")) {
            if (!num1.equals(num2)) {
                release(num2);
            }
            if (!num1.equals(num3)) {
                release(num3);
            }
        } else if (op.equals("beq") || op.equals("bne")) {
            release(num1);
        }

    }

    public void release(String addr) {
//        if (!Compiler.isPolish) {
//            if (addr.charAt(0) == '$' && addr.charAt(1) == 't') {
//                t_reg[addr.charAt(2) - '0'] = "DEFAULT";
//                generate("# RELEASE " + addr);
//            }
//        }
        if (addr.charAt(0) == '$' && addr.charAt(1) == 't') {
            t_reg[addr.charAt(2) - '0'] = "DEFAULT";
            generate("# RELEASE " + addr);
        }
    }

    public void releaseS_reg() {
        //当函数变化时清空所有s寄存器
        //按理说是基本块变化时清空才对
        for (int i = 0; i < 8; i++) {
            s_reg[i] = "DEFAULT";
        }
    }

    public String assign_s_reg(String name) {
        for (int i = 0; i < 8; i++) {
            if (s_reg[i].equals("DEFAULT")) {
                s_reg[i] = name;
                return "$s" + i;
            }
        }
        return "WRONG";
    }

    public String assign_t_reg(String name) {
        for (int i = 0; i < 10; i++) {
            if (t_reg[i].equals("DEFAULT")) {
                t_reg[i] = name;
                return "$t" + i;
            } else if (PseudoCodes.lastUseTmp.containsKey(t_reg[i])) {
                if (cur_line > PseudoCodes.lastUseTmp.get(t_reg[i])) { //当前行比最后用它还要大
                    generate("#release $t" + i + " because it would not be used anymore");
                    t_reg[i] = name;
                    return "$t" + i;
                }
            }
        }
        int i = PseudoCodes.lastUseTmp.get(t_reg[9]);
        int cur = cur_line;
        return "WRONG";
    }

    /**
     * 将name的值读进对应的寄存器里
     */
    public void load_value(String name, String reg) {
        boolean in_reg = in_reg(name) || assign_reg(name);
        String addr = name2addr(name);
        if (in_reg) {
            generate("move", reg, addr);
        } else if (is_number(name)) {
            generate("li", reg, addr);
        } else {
            generate("lw", reg, addr);
        }
    }

    /**
     * 将reg寄存器里的值存放到name的位置
     */
    public void save_value(String reg, String name) {
        boolean in_reg = in_reg(name) || assign_reg(name);
        String addr = name2addr(name);
        if (in_reg) {
            generate("move", addr, reg);
        } else if (!is_number(name)) {
            generate("sw", reg, addr);
        } else {
            System.out.println(name + " not in memory or reg!");
        }
    }

    /**
     * left = right 赋值语句操作
     *
     * @param left  = 左操作数
     * @param right = 右操作数
     */
    public void trans_assign(String left, String right) {
        boolean a_in_reg = in_reg(left) || assign_reg(left);
        String a = name2addr(left);
        String reg = "$a1";

        if (a_in_reg) { // a在寄存器里
            load_value(right, a);
        } else { //a在内存里
            boolean b_in_reg = in_reg(right) || assign_reg(right);
            String b = name2addr(right);
            if (b_in_reg) { //b在寄存器里
                generate("sw", b, a);
            } else if (is_number(right)) {
                generate("li", reg, b);
                generate("sw", reg, a);
            } else {
                generate("lw", reg, b);
                generate("sw", reg, a);
            }
        }
    }

    public boolean is_number(String name) {
        return utlis.isInteger(name) && !name.equals("0");
    }

    public boolean in_memory(String name) {
        return !is_number(name) && !in_reg(name);
    }

    public boolean in_reg(String name) {
        if (name.equals("0") || name.equals("%RET")) {
            return true;
        }
        if (is_number(name)) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (t_reg[i].equals(name)) {
                return true;
            }
        }
        for (int i = 0; i < 8; i++) {
            if (s_reg[i].equals(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否是数组传参
     * 如果带 ~ 说明就是部分传参
     * 如果是变量/常量/参数 且维数>0，说明为数组传参
     *
     * @param name
     */
    public boolean isTransArray(String name) {
        if (name.contains("~")) { //包含~代表部分传参
            return true;
        }
        if (utlis.isInteger(name)) {
            return false;
        }
        if (SymTable.getSFirstNotFunc(name) != null) {
            return Objects.requireNonNull(SymTable.getSFirstNotFunc(name)).dim > 0;
        }
        return false;
    }

    public String name2addrForArray(String name) {
        SymItem si = SymTable.getSFirstNotFunc(name);
        //就算是传一维数组，以下的手段按理说也可以得到正确的地址
        if (si.dim > 0) {
            if (si.cur_func.equals("GLOBAL")) {
                return "arr__" + name + "_";
            } else if (si.stiType == STIType.para) {
                //如果是参数，则从sp向上找，第n个参数就是 ($sp)+4*n, n从1开始计数
                int index = SymTable.indexInFunc(si.name, si.cur_func);
                //generate("addu", "$a3", "$sp", String.valueOf(index * 4));
                generate("lw", "$a3", index * 4 + "($sp)");
                return "$a3";
            } else {
                return String.valueOf(si.addr);
            }
        }
        System.out.println("这不是个数组");
        return null;
    }

    /**
     * 重要函数
     * 找到中间代码中名字为name的地址并返回
     * 如果name在寄存器中，则返回寄存器编号
     * 如果name在内存中，则返回绝对地址
     * 如果是常数，0则返回$zero，否则返回该数值
     * 特别地，对于数组的部分传参与全部传参，也会有特殊处理
     *
     * @param name
     * @return
     */
    public String name2addr(String name) {
        if (utlis.isInteger(name)) {
            if (name.equals("0")) {
                return "$zero";
            }
            return name;
        }

        //is "$sp"
        if (name.equals("$sp")) {
            return "$sp";
        }

        //in t register
        for (int i = 0; i < 10; i++) {
            if (t_reg[i].equals(name)) {
                return "$t" + i;
            }
        }
        //in s register
        for (int i = 0; i < 8; i++) {
            if (s_reg[i].equals(name)) {
                return "$s" + i;
            }
        }

        //todo 把中间变量存在内存里！

        //对于函数传数组，push的参数是没有经过处理的数组，例如 a[3]会是a~12, a[i]会是a~i*4
        //规定数组传参都传绝对地址,return回去的东西是我们要的地址本身
        //里面用到了$a2/$a3寄存器，前提是这块只有PUSH会用到，且保证PUSH不会用到$a2/$a3
        if (name.contains("~")) {
            String[] arr = name.split("~");
            String ident = arr[0];
            String offset = arr[1];
            SymItem si = SymTable.getSFirstNotFunc(ident);
            if (utlis.isInteger(offset)) {
                if (si.cur_func.equals("GLOBAL")) { //如果是全局变量，则地址为基地址+偏移
                    generate("li", "$a3", String.valueOf(si.addr + Integer.parseInt(offset)));
                    return "$a3";
                } else if (si.stiType == STIType.para) { //如果是参数，则向上找，index位置的里面存着我们要的基地址
                    int index = SymTable.indexInFunc(si.name, si.cur_func); //index里面存着基地址
                    generate("lw", "$a3", index * 4 + "($sp)");
                    generate("addiu", "$a3", "$a3", offset);
                    return "$a3";
                } else { //局部变量，则先算出相对sp的偏移 si.addr+offset
                    generate("addiu", "$a3", "$sp", String.valueOf(si.addr + Integer.parseInt(offset)));
                    return "$a3"; //应该是有问题的，这个是相对值
                }
            } else {
                String regOffset = name2addr(offset); //TODO: attention! 能找到这个寄存器吗？
                if (si.cur_func.equals("GLOBAL")) {
                    generate("addiu", "$a3", regOffset, String.valueOf(si.addr));
                    return "$a3";
                } else if (si.stiType == STIType.para) {
                    int index = SymTable.indexInFunc(si.name, si.cur_func); //index里面存着基地址
                    generate("lw", "$a3", index * 4 + "($sp)");
                    generate("addu", "$a3", "$a3", regOffset);
                    return "$a3";
                } else {
                    generate("addiu", "$a3", regOffset, String.valueOf(si.addr));
                    generate("addu", "$a3", "$a3", "$sp");
                    return "$a3";
                }
            }
        }
        SymItem si = SymTable.getSFirstNotFunc(name);
        //就算是传一维数组，以下的手段按理说也可以得到正确的地址
        if (si.dim > 0) {
            if (si.cur_func.equals("GLOBAL")) {
                generate("la", "$a3", "arr__" + name + "_");
                return "$a3";
            } else if (si.stiType == STIType.para) {
                //如果是参数，则从sp向上找，第n个参数就是 ($sp)+4*n, n从1开始计数
                int index = SymTable.indexInFunc(si.name, si.cur_func);
                //generate("addu", "$a3", "$sp", String.valueOf(index * 4));
                generate("lw", "$a3", index * 4 + "($sp)");
                return "$a3";
            } else {
                generate("addiu", "$a3", "$sp", String.valueOf(si.addr));
                return "$a3";
            }
        }

        if (si.cur_func.equals("GLOBAL")) {
            return String.valueOf(si.addr);
        } else if (si.stiType == STIType.para) {
            //如果是参数，则从sp向上找，第n个参数就是 ($sp)+4*n, n从1开始计数
            int index = SymTable.indexInFunc(si.name, si.cur_func);
            return index * 4 + "($sp)";
        } else { //如果是局部变量，则返回相对$sp的偏移
            return si.addr + "($sp)";
        }
    }

    public void generate_arth_with_polish(String instr, String a, String b, String c) {
        String reg3 = "$a3";
        if (instr.equals("mul")) {
            polishMul(a, b, c);
        } else if (instr.equals("addu")) {
            polishAdd(a, b, c);
        } else if (instr.equals("subu")) {
            polishSub(a, b, c);
        } else if (instr.equals("div")) {
            polishDiv(a, b, c);
        } else { // mol
            polishMol(a, b, c);
        }
    }

    public void generate_arth(String instr, String a, String b, String c) {
        if (isPolish) {
            generate_arth_with_polish(instr, a, b, c);
            return;
        }
        String reg3 = "$a3";
        if (instr.equals("mol")) {
            //要知道，如果能到这步，不可能出现b和c都是数字的情况
            if (is_number(b)) {
                generate("li", reg3, b);
                generate("div", reg3, c);
                generate("mfhi", a);
            } else if (is_number(c)) {
                generate("li", reg3, c);
                generate("div", b, reg3);
                generate("mfhi", a);
            } else { //b和c都不是number
                generate("div", b, c);
                generate("mfhi", a);
            }
            return;
        }
        if (b.equals("0")) { //b为0
            b = "$0";
        }
        if (is_number(b)) {
            if (instr.equals("addu")) {
                generate("addu", a, c, b);
            } else if (instr.equals("mul")) {
                generate(instr, a, c, b);
            } else if (instr.equals("div") || instr.equals("subu")) {
                generate("li", reg3, b);
                generate(instr, a, reg3, c);
            } else {
                generate(instr, a, b, c);
            }
        } else if (instr.equals("div") && !is_number(c)) {
            generate("div", b, c);
            generate("mflo", a);
            if (!a.equals(b)) {
                release(b);
            }
            if (!a.equals(c)) {
                release(c);
            }
        } else {
            generate(instr, a, b, c);
        }
    }

    public void generateSet(String instr, String a, String b, String c, String reg1, String reg2,
                            boolean b_in_reg, boolean c_in_reg, boolean b_is_number, boolean c_is_number) {
        if (b_is_number && c_is_number) {
            generate("li", reg1, b);
            generate("li", reg2, c);
            generate(instr, a, reg1, reg2);
        } else if (b_is_number) {
            if (c_in_reg) {
                generate("li", reg1, b);
                generate(instr, a, reg1, c);
            } else { //c在内存里
                generate("li", reg1, b);
                generate("lw", reg2, c);
                generate(instr, a, reg1, reg2);
            }
        } else if (c_is_number) {
            if (b_in_reg) {
                generate("li", reg1, c);
                generate(instr, a, b, reg1);
            } else { //b在内存里
                generate("li", reg1, c);
                generate("lw", reg2, b);
                generate(instr, a, reg2, reg1);
            }
        } else { //b c都不是数字
            if (b_in_reg && c_in_reg) {
                generate(instr, a, b, c);
            } else if (b_in_reg) { //b在寄存器，c在内存
                generate("lw", reg1, c);
                generate(instr, a, b, reg1);
            } else if (c_in_reg) { //c在寄存器，b在内存
                generate("lw", reg1, b);
                generate(instr, a, reg1, c);
            } else { //b c都在内存
                generate("lw", reg1, b);
                generate("lw", reg2, c);
                generate(instr, a, reg1, reg2);
            }
        }
    }

    public boolean assign_reg(String name) { //只有临时变量能分配到t寄存器
        if (name.charAt(0) == '#') {
            String treg = assign_t_reg(name);
            //分配到了寄存器
            return !treg.equals("WRONG");
        }
        SymItem si = SymTable.getSFirstNotFunc(name);
        if (si != null && isPolish) { //只有优化的时候才分配S寄存器
            if ((si.stiType == STIType.var) && !si.cur_func.equals("GLOBAL") && si.dim == 0) { //只有局部 0维 变量能分配到s寄存器
                String sreg = assign_s_reg(name);
                return !sreg.equals("WRONG");
            }
        }
        return false;
    }

    public void mid2mip() {
        //需要记得的事情：我一番骚操作，使得局部变量是从0开始的，这个的保障是因为Memory改动了起始位置
        generate(".data");
        //.data里面初始化全局数组和字符串
        //int space = 0; //space是记录了数组和字符串在.data段占了多少空间
        //最新的修改：把所有全局变量，包括0维，全存进.data里面
        for (SymItem si : SymTable.table) {
            if (si.cur_func.equals("GLOBAL")) {
                if (si.dim == 0) {
                    generate("globalTp__" + si.name + "_: .space 4");
                } else if (si.dim == 1) {
                    generate("arr__" + si.name + "_: .space " + si.dim1 * 4);
                } else {
                    generate("arr__" + si.name + "_: .space " + si.dim1 * si.dim2 * 4);
                }
            }
        }
        ArrayList<String> strcons = PseudoCodes.strcons;
        Map<String, Integer> strcon2index = new HashMap<>();
        String str;
        for (int i = 0; i < strcons.size(); i++) {
            if (!PseudoCodes.strcons.get(i).equals("$")) {
                str = strcons.get(i);
                if (!strcon2index.containsKey(str)) {
                    generate("str__" + i + ": .asciiz \"" + str + "\"");
                    strcon2index.put(str, i);
                }
            }
        }
        generate("newline__: .asciiz \"\\n\"");
        boolean init = true;
        SymTable.clear();
        //---------------------------------------------------
        generate(".text");
        for (PseudoCode code : midCodes) {
            if (PseudoCodes.getPcode(code) != null) {
                generate("\n#" + PseudoCodes.getPcode(code));
            }
            OPType op = code.getOp();
            String num1 = code.getNum1();
            String num2 = code.getNum2();
            String result = code.getResult();
            if (op == OPType.ADD_LAYER) {
                SymTable.reAddLayer();
            } else if (op == OPType.POP_LAYER) {
                SymTable.rePopLayer();
            } else if (op == OPType.CONST || op == OPType.VAR || op == OPType.PARA || op == OPType.ARR) {
                SymTable.reAddItem();
            } else if (op == OPType.FUNC) {
                SymTable.reAddItem();
                // in a new function
                if (init && !num2.equals("main")) { //如果没调用主函数, 且本条不是主函数，则调用主函数
                    generate("addiu $sp, $sp, -128");
                    generate("j main");
                }
                init = false; //只有第一次的时候需要判断一下，
                cur_func = num2;
                generate(cur_func + ":"); // 函数标签
                if (isPolish) {
                    releaseS_reg();
                }
            } else if (op == OPType.PRE_CALL) {
                paraNums.add(0); //初始化参数个数
                //1. 先计算位移 ——> 20*4+B函数的参数个数*4+A函数目前的局部变量个数*4+B中间变量参数个数*4
                //但是有一个很重要的问题，如果连续调用需要sp_offset叠加，因此每次初始化都是前几个之和
                int bParaNum = Objects.requireNonNull(SymTable.getFunc(num1)).paraNum;
                int aLocalNum = SymTable.getLocalNum(cur_func);
                int sp_offset = 20 * 4 + bParaNum * 4 + aLocalNum * 4;
                int sum = 0;
                if (!func_sp_offset.isEmpty()) {
                    sum = func_sp_offset.getLast();
                }
                sp_offset += sum; //因为需要加上之前偏移的大小
                func_sp_offset.add(sp_offset);
                generate("#func_sp_offset is: " + sp_offset);
            } else if (op == OPType.PUSH) {
                //2. 再填参数 ——> 参数填在： $sp - func_sp_offset + 4*paraNum
                int paraNum = paraNums.getLast();
                paraNum++; //参数是存在当前$sp指针上面的位置里,且无论是数组还是常数都只占一个int空间
                String reg1 = "$a1";
                int sp_offset = func_sp_offset.getLast();
                int paraPlace = paraNum * 4 - sp_offset;
                if (utlis.isInteger(num1)) {
                    if (num1.equals("0")) {
                        generate("sw", "$0", paraPlace + "($sp)");
                    } else {
                        generate("li", reg1, num1);
                        generate("sw", reg1, paraPlace + "($sp)");
                    }
                } else if (in_reg(num1) || assign_reg(num1)) { //在寄存器里
                    String num1Addr = name2addr(num1);
                    generate("sw", num1Addr, paraPlace + "($sp)");
                } else if (isTransArray(num1)) { //如果是传数组
                    //这个时候num1Addr就是我们要的数组地址了，因此不要再lw了
                    //TODO 赶紧优化你那个垃圾ALU
                    String num1Addr = name2addr(num1);
                    generate("sw", num1Addr, paraPlace + "($sp)");
                } else { //在内存里
                    String num1Addr = name2addr(num1);
                    generate("lw", reg1, num1Addr);
                    generate("sw", reg1, paraPlace + "($sp)");
                }
                paraNums.set(paraNums.size() - 1, paraNum); //其实就是+1
            } else if (op == OPType.CALL) {
                //3. 移动指针 ——> $sp = $sp - func_sp_offset
                int op_offset = func_sp_offset.getLast();
                generate("addiu", "$sp", "$sp", String.valueOf(-op_offset));
                //4. 填寄存器 ——> 从下往上：$ra, $t0~$t9, $s0~$s7
                int RAPOINTER = 0;
                int paraNum = paraNums.getLast();
                if (!cur_func.equals("main")) {
                    paraNum++; //存ra
                    generate("sw", "$ra", paraNum * 4 + "($sp)");
                    RAPOINTER = paraNum; //记录ra的位置
                }
                //保存现场的寄存器, 分配10个地方给t寄存器
                //注意了，数组拷贝是深拷贝...
                ArrayList<Integer> t_save = new ArrayList<>();
                String[] t_old = new String[10];
                System.arraycopy(t_reg, 0, t_old, 0, 10);//太tmd坑人了，数组是深拷贝，这样才是浅拷贝
                int T_START = paraNum + 1; //记录t寄存器起始的位置
                for (int i = 0; i < 10; i++) {
                    paraNum++;
                    if (!t_reg[i].equals("DEFAULT")) {
                        generate("sw", "$t" + i, paraNum * 4 + "($sp)");
                        t_reg[i] = "DEFAULT";
                        t_save.add(i);
                    }
                }
                //保存现场的寄存器, 分配8个地方给s寄存器
                ArrayList<Integer> s_save = new ArrayList<>();
                String[] s_old = new String[8];
                System.arraycopy(s_reg, 0, s_old, 0, 8);
                int S_START = paraNum + 1; //记录s寄存器起始的位置
                for (int i = 0; i < 8; i++) {
                    paraNum++;
                    if (!s_reg[i].equals("DEFAULT")) {
                        generate("sw", "$s" + i, paraNum * 4 + "($sp)");
                        s_reg[i] = "DEFAULT";
                        s_save.add(i);
                    }
                }
                generate("jal", num1);
                //------------函数返回-------------
                //5. 恢复寄存器
                if (!cur_func.equals("main")) {
                    generate("lw", "$ra", RAPOINTER * 4 + "($sp)");
                }
                //恢复t寄存器
                for (Integer i : t_save) {
                    generate("lw", "$t" + i, (T_START + i) * 4 + "($sp)");
                }
                System.arraycopy(t_old, 0, t_reg, 0, 10);
                //恢复s寄存器
                for (Integer i : s_save) {
                    generate("lw", "$s" + i, (S_START + i) * 4 + "($sp)");
                }
                System.arraycopy(s_old, 0, s_reg, 0, 8);
                //6. 指针移回来
                generate("addiu", "$sp", "$sp", String.valueOf(op_offset));
                func_sp_offset.removeLast(); //弹出最后一个元素
                paraNums.removeLast(); //弹出最后一个元素
            } else if (op == OPType.RETURN) {
                if (!num1.equals("DEFAULT")) { //有返回值
                    load_value(num1, "$v0");
                }
                if (cur_func.equals("main")) {
                    generate("li $v0, 10");
                    generate("syscall");
                } else {
                    generate("jr $ra");
                }
            } else if (op == OPType.GET_RET) { //todo check
                if (!in_reg(result)) {
                    assign_reg(result); //注意，这个地方必须成功
                }
                String addr = name2addr(result);
                generate("move", addr, "$v0");
            } else if (op == OPType.LABEL) {
                generate(num1 + ":");
            } else if (op == OPType.PRINTF) {
                if (num1.equals("\n")) {
                    generate("la $a0, newline__");
                    generate("li $v0, 4");
                    generate("syscall");
                } else if (num2.equals("STRCON")) {
                    generate("la $a0, str__" + strcon2index.get(num1));
                    generate("li $v0, 4");
                    generate("syscall");
                } else { //表达式 也就是%d对应的东西
                    load_value(num1, "$a0");
                    generate("li $v0, 1");
                    generate("syscall");
                }
            } else if (op == OPType.GET_INT) {
                if (num2.equals("DEFAULT")) { //此时不是读数组
                    generate("li $v0, 5");
                    generate("syscall");
                    save_value("$v0", num1);
                } else { //读数组
                    String reg1 = "$a1";
                    String reg2 = "$a2";
                    generate("li $v0, 5");
                    generate("syscall"); //现在结果在v0里面
                    //对于这个addr，如果是arr开头，说明是全局的，如果是$开头，说明是参数，如果是整数，说明是局部变量（相对sp的偏移量）
                    if (utlis.isInteger(num2)) { //偏移是整数
                        String addr = name2addrForArray(num1); //数组首地址
                        int index = Integer.parseInt(num2);
                        if (utlis.isInteger(addr)) { //是局部变量（相对sp的偏移
                            num2 = String.valueOf(index * 4 + Integer.parseInt(addr));
                            generate("sw", "$v0", num2 + "($sp)");
                        } else if (addr.charAt(0) == '$') { //在寄存器中，说明是参数
                            generate("sw", "$v0", index * 4 + "(" + addr + ")");
                        } else { //全局变量，以arr开头
                            if (index == 0) {
                                generate("sw", "$v0", addr);
                            } else {
                                generate("sw", "$v0", addr + "+" + index * 4); //数组首地址+偏移
                            }
                        }
                    } else {
                        String addr = name2addr(num1);
                        boolean num2_in_reg = in_reg(num2) || assign_reg(num2);
                        String num2Addr = name2addr(num2);
                        if (num2_in_reg) { //num2在寄存器里，直接*4
                            generate("sll", reg2, num2Addr, "2");
                            generate("addu", "$a1", addr, reg2);
                            generate("sw", "$v0", "0(" + reg1 + ")");
                        } else { //num2在内存里，需要先取出来再*4
                            generate("lw", reg2, num2Addr);
                            generate("sll", reg2, reg2, "2");
                            generate("addu", "$a1", addr, reg2);
                            generate("sw", "$v0", "0(" + reg1 + ")");
                        }
                    }
                }
            } else if (op == OPType.ASSIGN) {
                trans_assign(num1, num2);
            } else if (op == OPType.GOTO) {
                generate("j" + " " + num1);
            } else if (branch2instr.containsKey(op)) {
                /*
                    num1,num2都在寄存器
                    num1在内存 lw reg1, num1
                    num1,num2都在内存 lw reg1, num1, lw reg2, num2,
                 */
                boolean a_in_reg = in_reg(num1) || assign_reg(num1);
                boolean b_in_reg = in_reg(num2) || assign_reg(num2);

                String a = name2addr(num1);
                String b = name2addr(num2);

                String reg1 = "$a1";
                String reg2 = "$a2";

                String instr = branch2instr.get(op);

                if (a_in_reg && b_in_reg) { // a和b都在寄存器
                    generate(instr, a, b, result);
                } else if (a_in_reg) { // a在寄存器
                    if (is_number(num2)) {
                        generate(instr, a, b, result);
                    } else { //num2在内存里
                        generate("lw", reg1, b);
                        generate(instr, a, reg1, result);
                    }
                } else if (b_in_reg) { // b在寄存器
                    if (is_number(num1)) { // a为数字，例如 bgt 5, $t1, label
                        generate(branchTransfer.get(instr), b, a, result); //注意 换了位置
                    } else {
                        generate("lw", reg1, a);
                        generate(instr, reg1, b, result);
                    }
                } else { //a和b都在内存里或者其中一个在内存
                    if (is_number(num1)) { //a是数字,则b在内存
                        generate("lw", reg1, b);
                        generate(branchTransfer.get(instr), reg1, a, result); //换了顺序，因为bne必须第二个是数字
                    } else if (is_number(num2)) { //b是数字，则a在内存
                        generate("lw", reg1, a);
                        generate(instr, reg1, b, result);
                    } else { //都在内存
                        generate("lw", reg1, a);
                        generate("lw", reg2, b);
                        generate(instr, reg1, reg2, result);
                    }
                }
            } else if (op2instr.containsKey(op)) { // + - * /
                /* 对于a=b+c:
                 * abc都在寄存器/常量：                add a,b,c
                 * ab在寄存器/常量，c在内存（或反过来）： lw reg2,c  add a,b,reg2
                 * a在寄存器，bc在内存：               lw reg1,b  lw reg2,c  add a,reg1,reg2
                 * a在内存，bc在寄存器/常量：           add reg1,b,c  sw reg1,a
                 * ab在内存，c在寄存器/常量（或反过来）： lw reg1,b  add reg1,reg1,c  sw reg1,a
                 * abc都在内存：                     lw reg1,b  lw reg2,c  add reg1,reg1,reg2  sw reg1,a
                 */
                String instr = op2instr.get(op);
                String reg1 = "$a1";
                String reg2 = "$a2";
                boolean a_in_reg = in_reg(result) || assign_reg(result);
                boolean b_reg_const = in_reg(num1) || is_number(num1) || assign_reg(num1);
                boolean c_reg_const = in_reg(num2) || is_number(num2) || assign_reg(num2);


                String a = name2addr(result);
                String b = name2addr(num1);
                String c = name2addr(num2);


                if (a_in_reg) {//a在寄存器
                    if (b_reg_const && c_reg_const) {
                        generate_arth(instr, a, b, c);
                    } else if (b_reg_const) { // c在内存
                        generate("lw", reg1, c);
                        generate_arth(instr, a, b, reg1);
                    } else if (c_reg_const) { //b在内存
                        generate("lw", reg1, b);
                        generate_arth(instr, a, reg1, c);
                    } else {
                        generate("lw", reg1, b);
                        generate("lw", reg2, c);
                        generate_arth(instr, a, reg1, reg2);
                    }
                } else { //a在内存
                    if (b_reg_const && c_reg_const) {
                        generate_arth(instr, reg1, b, c);
                        generate("sw", reg1, a);
                    } else if (b_reg_const) { //c在内存
                        generate("lw", reg1, c);
                        generate_arth(instr, reg1, b, reg1);
                        generate("sw", reg1, a);
                    } else if (c_reg_const) { //b在内存
                        generate("lw", reg1, b);
                        generate_arth(instr, reg1, reg1, c);
                        generate("sw", reg1, a);
                    } else { //a,b,c都在内存
                        generate("lw", reg1, b);
                        generate("lw", reg2, c);
                        generate_arth(instr, reg1, reg1, reg2);
                        generate("sw", reg1, a);
                    }
                }
            } else if (op == OPType.ARR_SAVE || op == OPType.ARR_LOAD) {
                /* 对于a=b[c]:
                 * load c到reg  sll reg,reg,2
                 * a在寄存器，b为全局数组：lw a,b(reg)
                 * a在内存，b为全局数组： lw reg2,b(reg)  sw reg2, a
                 * a在寄存器，b为局部数组：add reg,reg,offset  add reg,reg,$sp  lw a,0(reg)
                 * a在内存，b为局部数组： add reg,reg,offset  add reg,reg,$sp  lw reg2,reg($sp)  sw reg2, a
                 */

                /* 对于b[c]=a:
                 * load c到reg  sll reg,reg,2
                 * a在寄存器，b为全局数组：sw a,b(reg)
                 * a在内存，b为全局数组： lw reg2,a  sw reg2,b(reg)
                 * a在寄存器，b为局部数组：add reg,reg,offset  sw a,b(reg)
                 * a在内存，b为局部数组： add reg,reg,offset  lw reg2,a  sw reg2,reg($sp)
                 * a为常量，b为全局数组：li reg2,a  sw a,b(reg)
                 * a为常量，b为局部数组：add reg,reg,offset  li reg2,a  sw reg2,reg($sp)
                 */
                //todo
                boolean a_in_reg = in_reg(result) || assign_reg(result);

                String a = name2addr(result);
                String reg1 = "$a1";
                String reg2 = "$a2";
                String item_addr = "0";
                SymItem si = SymTable.getSFirstNotFunc(num1);

                if (utlis.isInteger(num2)) { //数组内下标是常数，包括0
                    int offset = 4 * Integer.parseInt(num2);
                    if (si.cur_func.equals("GLOBAL")) { //是全局数组
                        item_addr = "arr__" + num1 + "_+" + (offset);
                    } else if (si.stiType != STIType.para) { //不是参数，则是局部数组
                        item_addr = (si.addr + offset) + "($sp)"; //注意，更改了一下数组的组织方式，局部数组从下到上存放
                    } else { //是参数
                        //把地址偏移存在$a1寄存器里，后面用到
                        int index = SymTable.indexInFunc(num1, si.cur_func); //index表示第几个参数
                        generate("lw", reg1, index * 4 + "($sp)");//参数在sp的上方存储,reg1里存放数组起始地址
                        //changed
                        item_addr = offset + "(" + reg1 + ")";
                    }
                } else { //下标在内存或者寄存器里
                    boolean index_in_reg = in_reg(num2) || assign_reg(num2);
                    String offset = name2addr(num2);
                    if (index_in_reg) {
                        generate("sll", reg1, offset, "2");
                    } else {
                        generate("lw", reg1, offset);
                        generate("sll", reg1, reg1, "2");
                    }
                    //此时reg1里存的数据就是offset
                    if (si.cur_func.equals("GLOBAL")) {
                        item_addr = "arr__" + num1 + "_(" + reg1 + ")";
                    } else if (si.stiType != STIType.para) {
                        generate_arth("addu", reg1, String.valueOf(si.addr), reg1); //计算偏移
                        generate_arth("addu", reg1, "$sp", reg1);
                        item_addr = "0(" + reg1 + ")"; //item_addr为相对于sp的偏移
                    } else {
                        //函数参数
                        int index = SymTable.indexInFunc(num1, si.cur_func); //index表示第几个参数
                        generate("lw", reg2, index * 4 + "($sp)");//参数在sp的上方存储,reg2里存放数组起始地址
                        generate("addu", reg1, reg1, reg2);//从数组首地址向高地址偏移offset
                        item_addr = "0(" + reg1 + ")";
                    }
                }

                if (op == OPType.ARR_LOAD) {
                    if (a_in_reg) {
                        generate("lw", a, item_addr);
                    } else {
                        generate("lw", reg2, item_addr);
                        generate("sw", reg2, a);
                    }
                } else {
                    if (a_in_reg) {
                        generate("sw", a, item_addr);
                    } else if (is_number(result)) {
                        generate("li", reg2, a);
                        generate("sw", reg2, item_addr);
                    } else {
                        generate("lw", reg2, a);
                        generate("sw", reg2, item_addr);
                    }
                }


            } else if (set2instr.containsKey(op)) {
                //这四种情况要求操作数不能是常数
                boolean a_in_reg = in_reg(result) || assign_reg(result);
                boolean b_in_reg = in_reg(num1) || assign_reg(num1);
                boolean c_in_reg = in_reg(num2) || assign_reg(num2);

                boolean b_is_number = is_number(num1);
                boolean c_is_number = is_number(num2);

                String reg1 = "$a1";
                String reg2 = "$a2";
                String instr = set2instr.get(op);

                String a = name2addr(result);
                String b = name2addr(num1);
                String c = name2addr(num2);

                if (a_in_reg) { //a在寄存器
                    generateSet(instr, a, b, c, reg1, reg2, b_in_reg, c_in_reg, b_is_number, c_is_number);
                } else { //a在内存
                    generateSet(instr, reg1, b, c, reg1, reg2, b_in_reg, c_in_reg, b_is_number, c_is_number);
                    generate("sw", reg1, a);
                }
            }
            cur_line++; //important,cur_line维护当前中间变量行数
        }
    }
    //-----------------------优化函数放在下面---------------------------

    /**
     * 乘法优化
     * a = b * c
     * 原理：1. 当一句乘法可以缩减成2句及以内的加法时，优化
     * 2. 当一句乘法里有2的幂时，可以用sll优化
     * 要求：传入的不能b和c都是数字
     */
    public void polishMul(String a, String b, String c) {
        if (utlis.isInteger(b) && utlis.isInteger(c)) {
            int d = Integer.parseInt(b) * Integer.parseInt(c);
            generate("li", a, String.valueOf(d));
            return;
        }
        if (utlis.isInteger(c)) { //利用乘法的可交换性
            String tmp = b;
            b = c;
            c = tmp;
        }
        if (utlis.isInteger(b)) {
            int intB = Integer.parseInt(b);
            if (intB == 0) { //a = 0 * c
                generate("addu", a, "$0", "$0"); //a = 0
            } else if (intB == 1) { // a = 1 * c
                generate("addu", a, c, "$0");
            } else if (intB == 2) { //a = 2 * c
                generate("addu", a, c, c); //a = c + c
            } else if (intB == 3) { //a = 3 * c
                generate("addu", a, c, c); // a = c + c
                generate("addu", a, a, c); // a = a + c
            } else if (utlis.isPowerOfTwo(intB)) { // a = 2^n * c
                int n = utlis.log2(intB); //n位
                generate("sll", a, c, String.valueOf(n)); // sll a, c, n
            } else {
                generate("mul", a, c, b);
            }
        } else { //没有数字
            generate("mul", a, b, c);
        }
    }

    /**
     * 加法优化
     * a = b + c
     * 原理：当有一个是数字时，转换为addiu
     * 当有一个是0且另外两个相同时，不作为
     * 要求：传入的b和c不能都是数字
     */
    public void polishAdd(String a, String b, String c) {
        if (utlis.isInteger(b) && utlis.isInteger(c)) {
            int d = Integer.parseInt(b) + Integer.parseInt(c);
            generate("li", a, String.valueOf(d));
            return;
        }
        if (utlis.isInteger(c)) {
            String tmp = b;
            b = c;
            c = tmp;
        }
        if (utlis.isInteger(b)) { // a = number + c
            if (b.equals("0") && a.equals(c)) { // a = a + 0;
                return;
            }
            generate("addiu", a, c, b);
        } else {
            generate("addu", a, b, c);
        }
    }

    /**
     * 减法优化
     * a = b - c
     */
    public void polishSub(String a, String b, String c) {
        if (utlis.isInteger(b) && utlis.isInteger(c)) {
            int d = Integer.parseInt(b) - Integer.parseInt(c);
            generate("li", a, String.valueOf(d));
            return;
        }
        String reg3 = "$a3";
        if (b.equals("0")) {
            b = "$0";
        }
        if (c.equals("0")) {
            c = "$0";
        }
        if (is_number(b)) {
            generate("li", reg3, b);
            generate("subu", a, reg3, c);
        } else {
            generate("subu", a, b, c);
        }
    }

    /**
     * 除法优化
     * a = b / c
     * 原理：1. 当c为数字且为2的幂时，可以用sra。注意，若b为负数，会出现问题，因此需要通过跳转语句做判断
     * 2. 当b为0时，不用除，直接为0
     * 3. 当c为1时，不用除，直接 a=b
     * 4. 因为div $t1, $t2, $t3的语句为4条，因此展开成 div $t2, $t3, mflo $t1
     */
    public void polishDiv(String a, String b, String c) {
        if (utlis.isInteger(b) && utlis.isInteger(c)) {
            int d = Integer.parseInt(b) / Integer.parseInt(c);
            generate("li", a, String.valueOf(d));
            return;
        }
        String reg3 = "$a3";
        if (b.equals("0")) { // a = 0 / c
            generate("li", a, "$0");
        } else if (is_number(b)) { // a = 4 / c
            generate("li", reg3, b);
            generate("div", reg3, c);
            generate("mflo", a);
        } else if (is_number(c)) {
            int intC = Integer.parseInt(c);
            int absC = Math.abs(intC);
            int index;
            for (index = 0; index < 31; index++) {
                if ((1 << index) == absC) {
                    break;
                }
            }
            if (intC == 1) { // a = b / 1 ----> a = b
                generate("addu", a, b, "$0"); //a=b+0
            } else if (intC == -1) { // a = b / -1 ------> a = -b
                generate("subu", a, "$0", b); //a = 0 - b
            } else if (index < 31 && intC > 0) {
                String label1 = "divLabel_" + divLabel + "_1";
                String label2 = "divLabel_" + divLabel + "_2";
                divLabel++;
                String logC = String.valueOf(utlis.log2(intC));
                generate("bgez", b, label1);
                generate("subu", reg3, "$0", b);
                generate("sra", a, reg3, logC);
                generate("subu", a, "$0", a);
                generate("j", label2);
                generate(label1 + ":");
                generate("sra", a, b, logC);
                generate(label2 + ":");
            } else if (index < 31 && intC < 0) {
                String label1 = "divLabel_" + divLabel + "_1";
                String label2 = "divLabel_" + divLabel + "_2";
                divLabel++;
                generate("bgez", b, label1);
                generate("addiu", reg3, b, String.valueOf(absC - 1));
                generate("sra", reg3, reg3, String.valueOf(index));
                generate("subu", a, "$0", reg3);
                generate("j", label2);
                generate(label1 + ":");
                generate("sra", reg3, b, String.valueOf(index));
                generate("subu", a, "$0", reg3);
                generate(label2 + ":");
            } else if (div2shift.containsKey(intC)) {
                int mgc = div2mag.get(intC);
                String label2 = "divLabel_" + divLabel + "_2";
                divLabel++;
                generate("li", reg3, String.valueOf(mgc));
                generate("mult", b, reg3);
                generate("mfhi", a);
                int shiftIndex = div2shift.get(intC);
                if (shiftIndex != 0) {
                    generate("sra", a, a, String.valueOf(shiftIndex));
                }
                generate("bgez", b, label2);
                generate("addiu", a, a, "1");
                generate(label2 + ":");
            } else {
                Integer exp = getExp(intC);
                if (exp != null) {
                    index = exp;
                    if (index < 31 && intC > 0) {
                        String label1 = "divLabel_" + divLabel + "_1";
                        String label2 = "divLabel_" + divLabel + "_2";
                        divLabel++;
                        generate("bgez", a, label1);
                        generate("addiu", reg3, b, String.valueOf(intC - 1));
                        generate("sra", b, reg3, String.valueOf(index));
                        generate("j", label2);
                        generate(label1 + ":");
                        generate("sra", b, b, String.valueOf(index));
                        generate(label2 + ":");
                    } else if (index < 31 && intC < 0) {
                        String label1 = "divLabel_" + divLabel + "_1";
                        String label2 = "divLabel_" + divLabel + "_2";
                        divLabel++;
                        generate("bgez", b, label1);
                        generate("addiu", reg3, b, String.valueOf(absC - 1));
                        generate("sra", reg3, reg3, String.valueOf(index));
                        generate("subu", b, "$0", reg3);
                        generate("j", label2);
                        generate(label1 + ":");
                        generate("sra", reg3, b, String.valueOf(index));
                        generate("subu", b, "$0", reg3);
                        generate(label2 + ":");
                    }
                    intC = Math.abs(intC) >> exp;
                    int mgc = div2mag.get(intC);
                    String label1 = "divLabel_" + divLabel + "_1";
                    String label2 = "divLabel_" + divLabel + "_2";
                    divLabel++;
                    String reg2 = "$a2";
                    generate("li", reg2, String.valueOf(mgc));
                    generate("mult", b, reg2);
                    generate("mfhi", a);
                    int shift = div2shift.get(intC);
                    if (shift != 0) {
                        generate("sra", a, a, String.valueOf(shift));
                    }
                    generate("bgez", b, label1);
                    generate("addiu", a, a, "1");
                    generate(label1 + ":");
                }
//                generate("li", reg3, c);
//                generate("div", b, reg3);
//                generate("mflo", a);
                else {
                    generate("li", reg3, c);
                    generate("div", b, reg3);
                    generate("mflo", a);
                }
            }
        } else { //b和c都不是数字
            generate("div", b, c);
            generate("mflo", a);
            if (!a.equals(b)) {
                release(b);
            }
            if (!a.equals(c)) {
                release(c);
            }
        }
    }

    private Integer getExp(int a) {
        int pos = Math.abs(a);
        for (int i = 1; i < 30; i++) {
            if (div2mag.containsKey(pos >> i)) {
                return i;
            }
        }
        return null;
    }

    /**
     * 模优化
     * 原理：1.当c为1或-1时，a为0；当b为0时，a为0
     * 2.当c为2的幂时，a = b&(c-1)，当然了，当b<0时还需做判断
     */
    public void polishMol(String a, String b, String c) {
        if (utlis.isInteger(b) && utlis.isInteger(c)) {
            int d = Integer.parseInt(b) % Integer.parseInt(c);
            generate("li", a, String.valueOf(d));
            return;
        }
        String reg3 = "$a3";
        if (is_number(b)) {
            if (Integer.parseInt(b) == 0) {
                generate("li", a, "0");
            } else {
                generate("li", reg3, b);
                generate("div", reg3, c);
                generate("mfhi", a);
            }
        } else if (is_number(c)) {
            int intC = Integer.parseInt(c);
            int absC = Math.abs(intC);
            int index;
            if (intC == 1 || intC == -1) { //模1或者模-1
                generate("li", a, "0");
            } else if (utlis.isPowerOfTwo(intC)) {
                String label1 = "molLabel_" + molLabel + "_1";
                String label2 = "molLabel_" + molLabel + "_2";
                molLabel++;
                String molC = String.valueOf(intC - 1); // 例如模4就是且3，模8就是且7
                generate("bgez", b, label1);
                generate("subu", reg3, "$0", b);
                generate("andi", a, reg3, molC);
                generate("subu", a, "$0", a);
                generate("j", label2);
                generate(label1 + ":");
                generate("andi", a, b, molC);
                generate(label2 + ":");
            } else if (div2mag.containsKey(intC)) {
                int mgc = div2mag.get(intC);
                int shiftIndex = div2shift.get(intC);
                String label1 = "molLabel_" + molLabel + "_1";
                String label2 = "molLabel_" + molLabel + "_2";
                molLabel++;
                generate("li", reg3, String.valueOf(mgc));
                generate("mult", reg3, b);
                generate("mfhi", reg3);
                if (shiftIndex != 0) {
                    generate("sra", reg3, reg3, String.valueOf(shiftIndex));
                }
                generate("bgez", b, label2);
                generate("addiu", reg3, reg3, "1");
                generate(label2 + ":");
                generate("mul", reg3, reg3, String.valueOf(intC));
                generate("subu", a, b, reg3);
            } else {
                int originC = intC;
                Integer exp = getExp(intC);
                if (exp != null) {
                    index = exp;
                    if (index < 31 && intC > 0) {
                        String label1 = "molLabel_" + molLabel + "_1";
                        String label2 = "molLabel_" + molLabel + "_2";
                        molLabel++;
                        generate("bgez", b, label1);
                        generate("addiu", reg3, b, String.valueOf(intC - 1));
                        generate("sra", reg3, reg3, String.valueOf(index));
                        generate("j", label2);
                        generate(label1 + ":");
                        generate("sra", reg3, b, String.valueOf(index));
                        generate(label2 + ":");
                    } else if (index < 31 && intC < 0) {
                        String label1 = "molLabel_" + molLabel + "_1";
                        String label2 = "molLabel_" + molLabel + "_2";
                        molLabel++;
                        generate("bgez", b, label1);
                        generate("addiu", reg3, b, String.valueOf(absC - 1));
                        generate("sra", reg3, reg3, String.valueOf(index));
                        generate("subu", reg3, "$0", reg3);
                        generate("j", label2);
                        generate(label1 + ":");
                        generate("sra", reg3, b, String.valueOf(index));
                        generate("subu", reg3, "$0", reg3);
                        generate(label2 + ":");
                    }
                    intC = Math.abs(intC) >> exp;
                    int mgc = div2mag.get(intC);
                    String label1 = "molLabel_" + molLabel + "_1";
                    molLabel++;
                    generate("mul", reg3, reg3, String.valueOf(mgc));
                    generate("mfhi", reg3);
                    int sft = div2shift.get(intC);
                    if (sft != 0) {
                        generate("sra", reg3, reg3, String.valueOf(sft));
                    }
                    generate("bgez", reg3, label1);
                    generate("addiu", reg3, reg3, "1");
                    generate(label1 + ":");
                    generate("mul", reg3, reg3, String.valueOf(originC));
                    generate("subu", a, b, reg3);
                } else {
                    generate("li", reg3, c);
                    generate("div", b, reg3);
                    generate("mfhi", a);
                }
            }
        } else { //b和c都不是number
            generate("div", b, c);
            generate("mfhi", a);
        }
    }


}

