import javax.xml.bind.annotation.XmlType;
import java.util.*;

public class PseudoCodes {
    static public ArrayList<PseudoCode> midCodes = new ArrayList<>(); //存中间代码的表
    static public ArrayList<tmpCode> tmpCodes = new ArrayList<>(); //存临时变量的表
    static public Map<String, Integer> func_addr = new HashMap<>();
    static public ArrayList<String> strcons = new ArrayList<>();
    static public Map<String, Integer> lastUseTmp = new HashMap<>();

    //--------optimize para---------
    static public Map<String, Integer> DAGMap = new HashMap<>();
    static public LinkedList<DAGNode> DAGNodes = new LinkedList<>();
    static public Map<String, ArrayList<BasicBlock>> basicBlocks = new HashMap<>();
    //-------optimize para end------

    static public int code_count = 0;
    static public int while_count = 0;
    static public int if_count = 0;
    static public int and_count = 0;

    static public LinkedList<Integer> whileStack = new LinkedList<>();

    static public String getWhileLabel(String place) {
        String result = null;
        switch (place) {
            case "START":
                result = "..while" + while_count + "..start";
                break;
            case "BODY":
                result = "..while" + while_count + "..body";
                break;
            case "END":
                result = "..while" + while_count + "..end";
                break;
        }
        return result;
    }

    static public String getIfLabel(String place) {
        String result = null;
        switch (place) {
            case "IF_BODY":
                result = "..if" + if_count + "..if_body";
                break;
            case "ELSE_BODY":
                result = "..if" + if_count + "..else_body";
                break;
            case "END":
                result = "..if" + if_count + "..end";
                if_count++;
                break;
        }
        return result;
    }

    static public String getAndLabel() {
        String label = "..and" + and_count + "..end";
        and_count++;
        return label;
    }

    static public String add(OPType op, String num1, String num2, String result) {
        midCodes.add(new PseudoCode(op, num1, num2, result));
        return result;
    }

    static public String addTmp(OPType op, String num1, String num2, String result, String cur_func) {
        if (result.equals("AUTO") || result.equals("AUTO_SLT") || result.equals("AUTO_RET") ||
                result.equals("AUTO_SLE") || result.equals("AUTO_SNE") || result.equals("AUTO_ARR_LOAD")) { //代表是加减乘除运算
            result = "#t" + code_count;
            code_count++;
            int addr = 0; //临时变量相对于自己的函数头偏移地址为addr;
            if (func_addr.containsKey(cur_func)) { //如果之前记录了这个函数，则addr+1
                addr = func_addr.get(cur_func) + 1;
                func_addr.put(cur_func, addr);
            } else { //否则记录一下这个addr
                func_addr.put(cur_func, 0);
            }
            tmpCodes.add(new tmpCode(result, cur_func, addr));
        }
        midCodes.add(new PseudoCode(op, num1, num2, result));
        return result;
    }

    static public void showTmpCodes() {
        for (tmpCode tc : tmpCodes) {
            tc.output();
        }
    }

    /**
     * 得到这个函数中间变量的个数
     * 从0开始编号
     *
     * @param funcName 函数名
     * @return 中间变量个数
     */
    static public int getFuncTmp(String funcName) {
        return func_addr.get(funcName);
    }

    static public tmpCode searchTmpCode(String name) {
        for (tmpCode tc : tmpCodes) {
            if (tc.name.equals(name)) {
                return tc;
            }
        }
        return null;
    }

    static public String setNotAdd(OPType op, String num1, String num2, String result, String cur_func) {
        if (result.equals("AUTO_SEQ")) { //等于置1
            String t1 = addTmp(OPType.SNE, num1, num2, "AUTO_SNE", cur_func); //t1为不等于置1
            result = addTmp(OPType.SUB, "1", t1, "AUTO", cur_func);
        }
        return result;
    }

    static public void addPrintItem(String printString, ArrayList<String> printNum) {
        char ch;
        //手动去除两侧的引号
        printString = printString.substring(1, printString.length() - 1);
        int len = printString.length();
        int i, j = 0;
        StringBuilder ans = new StringBuilder();
        for (i = 0; i < len; i++) {
            ch = printString.charAt(i);
            if (ch == '\\' && (i + 1) < len) {
                if (printString.charAt(i + 1) == 'n') {
                    if (!ans.toString().equals("")) {
                        add(OPType.PRINTF, ans.toString(), "STRCON", "DEFAULT");
                        strcons.add(ans.toString());
                        ans = new StringBuilder();
                    }
                    add(OPType.PRINTF, "\n", "DEFAULT", "DEFAULT");
                    strcons.add("$"); // $ 在formatestring里不合法
                    i++;
                    continue;
                }
            }
            if (ch == '%' && (i + 1) < len) {
                if (printString.charAt(i + 1) == 'd') { //%d
                    if (!ans.toString().equals("")) {
                        add(OPType.PRINTF, ans.toString(), "STRCON", "DEFAULT");
                        strcons.add(ans.toString());
                        ans = new StringBuilder();
                    }
                    add(OPType.PRINTF, printNum.get(j), "DEFAULT", "DEFAULT");
                    strcons.add("$"); // $ 在formatestring里不合法
                    j++;
                    i++; //                                                                        i -> d
                    continue;
                }
            }
            ans.append(printString.charAt(i));
        }
        if (ans.length() > 0) {
            add(OPType.PRINTF, ans.toString(), "STRCON", "DEFAULT");
            strcons.add(ans.toString());
        }
    }


    /**
     * 这个函数是补丁
     * 用于算出一个exp就输出，以免寄存器爆栈
     * 要求调用之前手动去除两侧引号
     */
    static public int pintItemAfterCal(String printString, String printNum, int index) {
        StringBuilder ans = new StringBuilder();
        int len = printString.length();
        int i;
        for (i = index; i < len; i++) {
            char ch = printString.charAt(i);
//            if (ch == '\\' && (i + 1) < len) {
//                if (printString.charAt(i + 1) == 'n') {
//                    if (!ans.toString().equals("")) {
//                        add(OPType.PRINTF, ans.toString(), "STRCON", "DEFAULT");
//                        strcons.add(ans.toString());
//                        ans = new StringBuilder();
//                    }
//                    add(OPType.PRINTF, "\n", "DEFAULT", "DEFAULT");
//                    strcons.add("$"); // $ 在formatestring里不合法
//                    i++;
//                    continue;
//                }
//            }
            if (ch == '%' && (i + 1) < len) {
                if (printString.charAt(i + 1) == 'd') { //%d
                    if (!ans.toString().equals("")) {
                        add(OPType.PRINTF, ans.toString(), "STRCON", "DEFAULT");
                        strcons.add(ans.toString());
                        ans = new StringBuilder();
                    }
                    add(OPType.PRINTF, printNum, "DEFAULT", "DEFAULT");
                    strcons.add("$"); // $ 在formatestring里不合法
                    i = i + 2;
                    break;
                }
            }
            ans.append(ch);
        }
        if (i >= len && ans.length() > 0) { //扫描结束了且还有没输出完的内容
            add(OPType.PRINTF, ans.toString(), "STRCON", "DEFAULT");
            strcons.add(ans.toString());
        }
        return i;
    }

    static public String printMips() {
        showTmpCodes();
        if (Compiler.isPolish) {
            for (String func : SymTable.foreverTable.keySet()) {
                for (SymItem item : SymTable.foreverTable.get(func)) {
                    System.out.println(func + "————" + item.toString());
                }
            }
            //divide_basic_block();
            //DAG_optimize();
            //const_broadcast();
            //polish_assign();
        }
        //在生成目标代码之前先记录一下每个中间变量最后一次使用的位置
        for (int i = 0; i < code_count; i++) {
            lastUseTmp.put("#t" + i, 0); //初始化为0
        }
        for (int i = 0; i < midCodes.size(); i++) {
            PseudoCode pc = midCodes.get(i);
            if (pc.getResult().charAt(0) == '#') { //以#开始说明是中间变量
                lastUseTmp.put(pc.getResult(), i);
            }
            if (pc.getNum1().charAt(0) == '#') {
                lastUseTmp.put(pc.getNum1(), i);
            }
            if (pc.getNum2().charAt(0) == '#') {
                lastUseTmp.put(pc.getNum2(), i);
            }
        }
        MipsGenerate mips = new MipsGenerate();
        //System.out.println(s);
        List<String> stringMips = new ArrayList<>(mips.getMips());
        return String.join("\n", stringMips);
    }

    static public String printMidCode() {
        List<String> ret = new ArrayList<>();
        for (PseudoCode pc : midCodes) {
            String ans = getPcode(pc);
            if (ans != null) {
                ret.add(ans);
            } else {
                ret.add(pc.getOp().toString());
            }
        }
        return String.join("\n", ret);
    }

    static public String getPcode(PseudoCode pc) {
        switch (pc.getOp()) {
            case ADD:
                return (pc.getResult() + " = " + pc.getNum1() + " + " + pc.getNum2());
            case SUB:
                return (pc.getResult() + " = " + pc.getNum1() + " - " + pc.getNum2());
            case MUL:
                return (pc.getResult() + " = " + pc.getNum1() + " * " + pc.getNum2());
            case DIV:
                return (pc.getResult() + " = " + pc.getNum1() + " / " + pc.getNum2());
            case MOL:
                return (pc.getResult() + " = " + pc.getNum1() + " % " + pc.getNum2());
            case ASSIGN:
                return (pc.getNum1() + " = " + pc.getNum2());
            case FUNC:
                return (pc.getNum1() + " " + pc.getNum2()); //num1为返回值类型，num2为函数名称
            case PRE_CALL:
                return ("pre_call: " + pc.getNum1());
            case PUSH:
                return ("push " + pc.getNum1());
            case CALL:
                return ("call " + pc.getNum1());
            case RETURN:
                if (pc.getNum1().equals("DEFAULT")) {//无返回值
                    return ("ret");
                } else { //有返回值
                    return ("ret " + pc.getNum1());
                }
            case GET_RET:
                return (pc.getResult() + " = RET");
            case VAR:
                if (!pc.getNum2().equals("DEFAULT")) {
                    return ("var int " + pc.getNum1() + " = " + pc.getNum2());
                } else {
                    return ("var int " + pc.getNum1());
                }
            case CONST:
                return ("const int " + pc.getNum1() + " = " + pc.getNum2());
            case PARA:
                return ("para int " + pc.getNum1());
            case ARR:
                return ("arr int " + pc.getNum1() + "[" + pc.getNum2() + "]");
            case ARR_SAVE:
                return (pc.getNum1() + "[" + pc.getNum2() + "]" + " = " + pc.getResult());
            case ARR_LOAD:
                return (pc.getResult() + " = " + pc.getNum1() + "[" + pc.getNum2() + "]");
            case GET_INT:
                if (pc.getNum2().equals("DEFAULT")) {
                    return (pc.getNum1() + " = getint");
                } else {
                    return (pc.getNum1() + "[" + pc.getNum2() + "]" + " = " + "getint");
                }
            case PRINTF:
                return ("print " + pc.getNum1());
            case BEQ:
                return ("beq " + pc.getNum1() + " " + pc.getNum2() + " " + pc.getResult());
            case BNE:
                return ("bne " + pc.getNum1() + " " + pc.getNum2() + " " + pc.getResult());
            case BGT:
                return ("bgt " + pc.getNum1() + " " + pc.getNum2() + " " + pc.getResult());
            case BGE:
                return ("bge " + pc.getNum1() + " " + pc.getNum2() + " " + pc.getResult());
            case BLT:
                return ("blt " + pc.getNum1() + " " + pc.getNum2() + " " + pc.getResult());
            case BLE:
                return ("ble " + pc.getNum1() + " " + pc.getNum2() + " " + pc.getResult());
            case SLT:
                return ("slt " + pc.getResult() + ", " + pc.getNum1() + ", " + pc.getNum2());
            case SLE:
                return ("sle " + pc.getResult() + ", " + pc.getNum1() + ", " + pc.getNum2());
            case SNE:
                return ("sne " + pc.getResult() + ", " + pc.getNum1() + ", " + pc.getNum2());
            case LABEL:
                return (pc.getNum1() + " :");
            case GOTO:
                return ("goto " + pc.getNum1());
        }
        return null;
    }

    //---------------------Optimize-------------------------------

    static public void const_broadcast() {
        String cur_func = "GLOBAL";
        ArrayList<PseudoCode> new_midCodes = new ArrayList<>();
        for (PseudoCode pc : midCodes) {
            PseudoCode newPc = new PseudoCode(pc.getOp(), pc.getNum1(), pc.getNum2(), pc.getResult());
            new_midCodes.add(newPc);
        }
        for (int i = 0; i < new_midCodes.size(); i++) {
            PseudoCode pc = new_midCodes.get(i);
            if (pc.getOp() == OPType.FUNC) {
                cur_func = pc.getNum2();
                continue;
            }
            if (pc.getOp() == OPType.ASSIGN &&
                    pc.getNum1().charAt(0) != '#' &&
                    utlis.isInteger(pc.getNum2())) {
                String ident = pc.getNum1();
                boolean isReValue = false;
                for (int j = i + 1; j < new_midCodes.size(); j++) {
                    PseudoCode pcLater = new_midCodes.get(j);
                    if (pcLater.getOp() == OPType.END_FUNC && !cur_func.equals("GLOBAL")) {
                        break;
                    } else if ((pcLater.getOp() == OPType.ASSIGN || pcLater.getOp()==OPType.GET_INT) && pcLater.getNum1().equals(ident)) {
                        //二次赋值
                        isReValue = true;
                        break;
                    } else if ((pcLater.getOp() == OPType.VAR || pcLater.getOp() == OPType.CONST) && pcLater.getNum1().equals(ident)) {
                        isReValue = true;
                        break;
                    }
                }
                if (!isReValue) {
                    for (int j = i + 1; j < new_midCodes.size(); j++) {
                        PseudoCode pcLater = new_midCodes.get(j);
                        new_midCodes.remove(pcLater);
                        String num1 = pcLater.getNum1(), num2 = pcLater.getNum2(), result = pcLater.getResult();
                        if (pcLater.getNum1().equals(ident)) {
                            num1 = pc.getNum2();
                        }
                        if (pcLater.getNum2().equals(ident)) {
                            num2 = pc.getNum2();
                        }
                        if (pcLater.getResult().equals(ident)) {
                            result = pc.getNum2();
                        }
                        new_midCodes.add(j, new PseudoCode(pcLater.getOp(), num1, num2, result));
                    }
                }
            }
        }
        midCodes = new_midCodes;
    }


    static public void polish_assign() {
        ArrayList<PseudoCode> new_midCodes = new ArrayList<>();
        PseudoCode c1;
        PseudoCode c2;
        for (int i = 0; i < midCodes.size() - 1; i++) {
            c1 = midCodes.get(i);
            c2 = midCodes.get(i + 1);
            if (c2.getOp() == OPType.ASSIGN && c2.getNum1().charAt(0) != '#' &&
                    c1.getResult().charAt(0) == '#' && c1.getResult().equals(c2.getNum2()) &&
                    (is_arth(c1.getOp()) || c1.getOp() == OPType.ARR_LOAD)) {
                //如果c1是给中间变量赋值或者load数组，c2是把上一个中间变量的值赋给局部变量
                new_midCodes.add(new PseudoCode(c1.getOp(), c1.getNum1(), c1.getNum2(), c2.getNum1()));
                i++;
            } else if (c1.getOp() == OPType.ASSIGN && c1.getNum1().charAt(0) == '#' &&
                    c1.getNum1().equals(c2.getNum1()) && (is_arth(c2.getOp()) || c2.getOp() == OPType.ARR_SAVE)) {
                //如果c1和c2都是给一个中间变量赋值，则不要c1了
                new_midCodes.add(c2);
                i++;
            } else if (c1.getOp() == OPType.ASSIGN && c1.getNum1().charAt(0) == '#' && c1.getNum1().equals(c2.getNum2()) &&
                    (is_arth(c2.getOp()) || c2.getOp() == OPType.ARR_SAVE)) {
                //当c2用到的内容是c1时，可以省去给中间变量赋值的过程
                new_midCodes.add(new PseudoCode(c2.getOp(), c2.getNum1(), c1.getNum2(), c2.getResult()));
                i++;
            } else if (i == midCodes.size() - 2) {
                new_midCodes.add(c1);
                new_midCodes.add(c2);
            } else {
                new_midCodes.add(c1);
            }
        }
        midCodes = new_midCodes;
    }

    /**
     * 划分基本块
     * 约定：最后把函数名-函数内基本块 数据存入blocks中
     * 每一个函数有一个基本块set，里面存Integer，意思是以integer划分为不同的块
     * 函数入口为第一条，label本身为一条，跳转语句的后一条
     */
    static public void divide_basic_block() {
        Map<String, TreeSet<Integer>> basic_block_index = new HashMap<>();
        String cur_func = "GLOBAL";
        for (int i = 0; i < midCodes.size(); i++) {
            PseudoCode c = midCodes.get(i);
            OPType op = c.getOp();
            if (op == OPType.FUNC) {
                cur_func = c.getNum2();//num2为函数名称
                basic_block_index.put(cur_func, new TreeSet<>());
                //函数的第一条语句为入口语句
                //todo 检查main函数最后有没有加endfunc
                for (int j = i + 1; midCodes.get(j).getOp() != OPType.END_FUNC; j++) {
                    if (midCodes.get(j + 1).getOp() == OPType.END_FUNC) { //下一个就是结束了，加进去
                        basic_block_index.get(cur_func).add(j + 1);
                    }
                    if (midCodes.get(j).getOp() != OPType.LABEL) { //非label则是函数的第一条语句了
                        basic_block_index.get(cur_func).add(j);
                        break;
                    }
                }
            } else if (cur_func.equals("GLOBAL")) {
                continue;
            }
            //遇到这几个东西，后面一句话得划分基本块了(后面一句话最好别是label)
            if (op == OPType.LABEL || op == OPType.CALL || op == OPType.RETURN || isJump(op)) {
                for (int j = i + 1; midCodes.get(j).getOp() != OPType.END_FUNC; j++) {
                    if (midCodes.get(j + 1).getOp() == OPType.END_FUNC) {
                        basic_block_index.get(cur_func).add(j + 1);
                    }
                    if (midCodes.get(j).getOp() != OPType.LABEL) {
                        basic_block_index.get(cur_func).add(j);
                        break;
                    }
                }
            }
            if (op == OPType.END_FUNC) {
                basic_block_index.get(cur_func).add(i);
            }
        }
        int index = 0;
        //由于basic_block_index里面的value是TreeMap，因此自动排好序了
        for (String func : basic_block_index.keySet()) {
            basicBlocks.put(func, new ArrayList<>());
            ArrayList<Integer> idxsList = new ArrayList<>(basic_block_index.get(func));
            for (int i = 0; i < idxsList.size() - 1; i++) {
                basicBlocks.get(func).add(new BasicBlock(index++, idxsList.get(i), idxsList.get(i + 1) - 1));
            }
        }
        //------------print basic_blocks------------------
        System.out.println("--------------basic blocks below-----------------------");
        for (String func : basicBlocks.keySet()) {
            System.out.println("------function " + func + " -----------");
            for (BasicBlock bb : basicBlocks.get(func)) {
                System.out.println(bb.index + ", " + bb.begin + ", " + bb.end);
            }
        }
    }

    /**
     * 基本块内生成DAG图
     *
     * @param begin 基本块起始位置
     * @param end   基本块终止位置
     */
    static public void generateDAG(int begin, int end) {
        for (int index = begin; index <= end; index++) {
            PseudoCode c = midCodes.get(index);
            String num1 = c.getNum1();
            String num2 = c.getNum2();
            String result = c.getResult();
            if (c.getOp() == OPType.ASSIGN) {
                // num1 = num2
                int k = DAGMap.getOrDefault(num2, -1);
                if (k == -1) { //num2不在记录栈里
                    k = DAGNodes.size();
                    DAGNodes.addLast(new DAGNode(k, num2, true));
                    DAGMap.put(num2, k);
                }
                DAGNodes.get(k).symbols.addLast(num1);

                DAGMap.put(num1, k); //如果num1存在，则直接替换，否则加入
                continue;
            }
            //op不是assign，而是别的
            int i = DAGMap.getOrDefault(num1, -1);
            if (i == -1) {
                i = DAGNodes.size();
                DAGNodes.addLast(new DAGNode(i, num1, true));
                DAGMap.put(num1, i);
            }

            int j = DAGMap.getOrDefault(num2, -1);
            if (j == -1) {
                j = DAGNodes.size();
                DAGNodes.addLast(new DAGNode(j, num2, true));
                DAGMap.put(num2, j);
            }

            int k = -1; //找到op一样且左右孩子一样的结点
            for (DAGNode node : DAGNodes) {
                if (node.name.equals(c.getOp().toString()) &&
                        node.children.size() > 1) {
                    if (node.children.get(0) == i && node.children.get(1) == j) {
                        k = node.index;
                        break;
                    }
                }
            }
            if (k == -1) { //没找到，给孩子加父母，给父母加孩子
                k = DAGNodes.size();
                DAGNode newNode = new DAGNode(k, c.getOp().toString(), false);
                newNode.children.add(i);
                newNode.children.add(j);
                DAGNodes.get(i).parents.add(k);
                DAGNodes.get(j).parents.add(k);
                DAGNodes.addLast(newNode);
            }
            DAGNodes.get(k).symbols.addLast(result);

            DAGMap.put(result, k);
        }

        //下面给每个节点设置primary_symbol
        for (DAGNode dag : DAGNodes) {
            boolean found = false;
            for (String name : dag.symbols) {
                if (name.charAt(0) != '#') {
                    dag.primary_symbol = name;
                    found = true;
                    break;
                }
            }
            if (!found) {
                dag.primary_symbol = dag.symbols.get(0);
            }
        }
    }

    /**
     * 从DAG图重新导出中间代码
     */
    static public ArrayList<PseudoCode> DAG_output_codes() {
        ArrayList<PseudoCode> ret = new ArrayList<>();
        LinkedList<DAGNode> queue = new LinkedList<>();
        while (true) {
            boolean end_flag = true; //end_flag: 标记DAG图中是否有没有入列的中间节点
            for (DAGNode dag : DAGNodes) {
                if (!dag.is_leaf && !dag.in_queue) {
                    end_flag = false;
                    break;
                }
            }
            if (end_flag) {
                break;
            }

            int i;
            for (i = 0; i < DAGNodes.size(); i++) {
                DAGNode dag = DAGNodes.get(i);
                if (dag.parents.isEmpty() && !dag.is_leaf && !dag.in_queue) {
                    queue.addLast(dag);
                    dag.in_queue = true;
                    break;
                }
            }
            //当i加入了队列后，就把i在别人的parent里清空
            //为了达到"选择自己没有进过队列且自己的parent都进过队列的点"
            remove_inqueue_parent(i);

            //如果i还有孩子，就把它符合条件的最左孩子加进queue，并顺着加下去
            if (!DAGNodes.get(i).children.isEmpty()) {
                int child_id = DAGNodes.get(i).children.get(0); //第一个孩子的id
                DAGNode child = DAGNodes.get(child_id);
                //只有有child满足把它加入到节点队列里的条件
                while (child.parents.isEmpty() && !child.is_leaf && !child.in_queue) {
                    queue.addLast(child);
                    child.in_queue = true;
                    remove_inqueue_parent(child_id);
                    if (!child.children.isEmpty()) {
                        child_id = child.children.get(0);
                        child = DAGNodes.get(child_id);
                    } else {
                        break;
                    }
                }
            }
        }

        for (int i = queue.size() - 1; i >= 0; i--) {
            boolean has_print = false;
            DAGNode queueItem = queue.get(i);
            for (String name : queue.get(i).symbols) {
                if (name.charAt(0) != '#') { //不是临时变量
                    if (queueItem.name.equals("ARR_SAVE")) {
                        ret.add(new PseudoCode(string2op(queueItem.name), name, DAGNodes.get(queueItem.children.get(0)).primary_symbol,
                                DAGNodes.get(queueItem.children.get(1)).primary_symbol));
                    } else {
                        ret.add(new PseudoCode(string2op(queueItem.name), DAGNodes.get(queueItem.children.get(0)).primary_symbol,
                                DAGNodes.get(queueItem.children.get(1)).primary_symbol, name));
                    }
                    has_print = true;
                }
            }
            if (!has_print) {
                if (queueItem.name.equals("ARR_SAVE")) {
                    ret.add(new PseudoCode(string2op(queueItem.name), queueItem.primary_symbol, DAGNodes.get(queueItem.children.get(0)).primary_symbol,
                            DAGNodes.get(queueItem.children.get(1)).primary_symbol));
                } else {
                    ret.add(new PseudoCode(string2op(queueItem.name), DAGNodes.get(queueItem.children.get(0)).primary_symbol,
                            DAGNodes.get(queueItem.children.get(1)).primary_symbol, queueItem.primary_symbol));
                }
            }
        }
        //移动了位置，但是对吗？
        for (DAGNode dag : DAGNodes) {
            if (dag.is_leaf) {
                for (String name : dag.symbols) {
                    if (name.charAt(0) != '#' && !name.equals(dag.primary_symbol) && DAGMap.get(name) == dag.index) {
                        ret.add(new PseudoCode(OPType.ASSIGN, name, dag.primary_symbol, "DEFAULT"));
                    }
                }
            }
        }

        return ret;
    }

    private static void remove_inqueue_parent(int child_id) {
        for (DAGNode dag : DAGNodes) {
            ArrayList<Integer> new_parents = new ArrayList<>();
            for (Integer j : dag.parents) {
                if (j != child_id) {
                    new_parents.add(j);
                }
            }
            dag.parents = new_parents;
        }
    }

    /**
     * DAG图利用
     * 将基本块内进行DAG优化
     */
    public static void DAG_optimize() {
        for (String it : basicBlocks.keySet()) {
            for (BasicBlock bb : basicBlocks.get(it)) {
                for (int i = bb.begin; i < bb.end; i++) {
                    if (!is_arth_or_assign(midCodes.get(i).getOp())) {
                        continue;
                    }
                    System.out.println("------BEFORE DAG-------");
                    midCodes.get(i).output();
                    int j = i + 1;
                    while (is_arth_or_assign(midCodes.get(j).getOp())) {
                        midCodes.get(j).output();
                        j++;
                    }
                    if (j - i <= 1) {
                        continue;
                    }

                    System.out.println("DAG FOR: " + i + "~" + (j - 1));
                    generateDAG(i, j - 1);
                    show_dag();
                    System.out.println("----gen dag done------");
                    if (!DAGNodes.isEmpty()) {
                        ArrayList<PseudoCode> dag_out = DAG_output_codes();
                        for (int m = i; m <= j - 1; m++) {
                            if (m - i < dag_out.size()) {
                                //下面两个操作相当于把m的位置替换为dag_out.get(m-i)
                                midCodes.add(m, dag_out.get(m - i));
                                midCodes.remove(m + 1);
                            } else {
                                midCodes.add(m, new PseudoCode(OPType.PLACE_HOLDER, "DEFAULT", "DEFAULT", "DEFAULT"));
                                midCodes.remove(m + 1);
                            }
                        }
                    }
                    i = j;

                    DAGNodes.clear();
                    DAGMap.clear();
                }
            }
        }
        ArrayList<PseudoCode> new_midCodes = new ArrayList<>();
        for (PseudoCode code : midCodes) {
            if (code.getOp() != OPType.PLACE_HOLDER) {
                new_midCodes.add(code);
            }
        }
        midCodes = new_midCodes;
    }

    private static void show_dag() {
        DAGNode root = new DAGNode(-1, "ROOT", false);
        for (DAGNode dag : DAGNodes) {
            if (dag.parents.isEmpty()) {
                root.children.add(dag.index);
            }
        }
        dfs_show_dag(root, 0);
    }

    private static void dfs_show_dag(DAGNode node, int depth) {
        for (int i = 0; i < depth - 1; i++) {
            System.out.println("|       ");
        }
        if (depth != 0) {
            System.out.println("|-----");
        }
        System.out.print("'" + node.name + "' " + node.primary_symbol + "(" + node.index + ") [");
        for (String name : node.symbols) {
            if (!name.equals(node.primary_symbol)) {
                System.out.print(name + ", ");
            }
        }
        System.out.println("]" + (node.is_leaf ? " isLeaf" : ""));
        for (Integer child : node.children) {
            dfs_show_dag(DAGNodes.get(child), depth + 1);
        }
    }


    static public boolean isJump(OPType op) {
        return op == OPType.GOTO || op == OPType.BEQ || op == OPType.BNE || op == OPType.BGE || op == OPType.BGT
                || op == OPType.BLE || op == OPType.BLT;
    }


    private static boolean is_arth_or_assign(OPType op) {
        return op == OPType.ADD || op == OPType.SUB || op == OPType.DIV ||
                op == OPType.MUL || op == OPType.MOL || op == OPType.ASSIGN;
    }

    private static boolean is_arth(OPType op) {
        return op == OPType.ADD || op == OPType.SUB || op == OPType.DIV ||
                op == OPType.MUL || op == OPType.MOL;
    }

    private static OPType string2op(String string) {
        for (OPType op : OPType.values()) {
            if (op.toString().equals(string)) {
                return op;
            }
        }
        System.out.println("ERROR, 这个字符不是OPType！");
        return null;
    }

}


//---------------------Classes Below--------------------------

class PseudoCode {
    private OPType op;
    private String num1;
    private String num2;
    private String result;

    public PseudoCode(OPType op, String num1, String num2, String result) {
        this.op = op;
        this.num1 = num1;
        this.num2 = num2;
        this.result = result;
    }

    public OPType getOp() {
        return op;
    }

    public String getNum1() {
        return num1;
    }

    public String getNum2() {
        return num2;
    }

    public String getResult() {
        return result;
    }

    public void output() {
        System.out.println(op.toString() + ", " + num1 + ", " + num2 + ", " + result);
    }
}

class tmpCode {
    int addr;
    String cur_func;
    String name;
    SymItem sym;

    public tmpCode(String name, String cur_func, int addr) {
        this.name = name;
        this.addr = addr;
        this.cur_func = cur_func;
        Token tk = new Token("tmp", name, 0, 0, 0);
        this.sym = new SymItem(tk, addr, cur_func, name, STIType.tmp, DataType.int_, 0, 0, 0, 0, null);
    }

    public void output() {
        System.out.println("name: " + name + ", func: " + cur_func + ", addr: " + addr);
    }
}

class BasicBlock {
    int index;
    int begin;
    int end;

    public BasicBlock(int index, int begin, int end) {
        this.index = index;
        this.begin = begin;
        this.end = end;
    }
}

class DAGNode {
    int index; //编号
    String name; //名字
    boolean is_leaf;
    boolean in_queue = false;
    LinkedList<String> symbols = new LinkedList<>(); //这个变量对应的符号
    String primary_symbol;
    ArrayList<Integer> children = new ArrayList<>();
    ArrayList<Integer> parents = new ArrayList<>();

    public DAGNode(int index, String name, boolean is_leaf) {
        this.index = index;
        this.name = name;
        this.is_leaf = is_leaf;
        if (is_leaf) { //如果是叶节点，自己代表的符号就是自己
            symbols.add(name);
        }
    }

    public void output() {
        System.out.println("index: " + index + ", name: " + name + ", isleaf: " + is_leaf +
                ", primary_symbol: " + primary_symbol);
    }
}

enum OPType {
    ADD, SUB, MUL, DIV, MOL, // calculate operation
    ASSIGN, // assign
    PRINTF, // write
    GET_INT, //read
    FUNC, END_FUNC, //function def
    PRE_CALL, PUSH, CALL, RETURN, GET_RET, //function operation
    VAR, CONST, PARA, ARR, //const def and var def and func para
    ARR_LOAD, ARR_SAVE, //array operation
    LABEL,
    GOTO, BNE, BEQ, BGT, BGE, BLT, BLE,
    SEQ, SNE, SLT, SLE,
    POP_LAYER, ADD_LAYER, //辅助操作，不输出
    PLACE_HOLDER,//占位符
}

/**
 * 对于变量声明 -> VAR/CONST, num1为变量名，num2为具体的值，若var没有定义则缺省，return缺省
 * 对于数组声明 -> ARR, num1为数组名，num2为数组空间大小，return缺省 二维降维为dim1*dim2
 * 对于函数声明 -> FUNC，num1为返回值类型，num2为函数名称，return缺省，class funcdef，注意：返回值类型非纯正确
 * 对于函数结束 -> END_FUNC，num1为返回值类型，num2为函数名称，return缺省，
 * 对于函数参数para -> 参数声明，num1为参数名称，other缺省, symtable addvar
 * 对于加减乘除模 -> 运算符，num1为第一个操作数，num2为第二个操作数，return为AUTO，分配临时寄存器 //对于一元表达式，即+5, -6, +-+-4这种情况，视为0+5, 0-6等
 * 对于赋值表达式 -> ASSIGN，num1为左值，num2为右值，return为缺省， 在stmt-> LVal = exp中
 * 对于函数调用先调 -> PRE_CALL,num1为函数名称。PRE_CALL的作用在于提前给好函数名称，以知道函数所需空间大小
 * 对于函数实参调用 -> PUSH, num1为push的内容，num2缺省，return缺省
 * 对于函数调用 -> CALL，num1为函数名称，num2缺省，
 * 对于函数调用返回 ->GET_RET, num1,num2缺省，return为"AUTO_RET",分配寄存器
 * 对于数组读取 -> ARR操作，num1为数组ident，num2为数组内位移，return缺省
 * 对于存数组 -> ARR_SAVE, num1为数组ident，num2为数组内位移，return为存的东西
 * 对于取数组 -> ARR_LOAD, num1为数组ident，num2为数组内位移，return为"AUTO_ARR_LOAD"，返回分配的
 * 对于函数返回 -> RETURN， 如果没有返回值，三个都是缺省， 有返回值， num1为返回，其它缺省
 * 对于函数的返回值，规定如下：
 * 1.如果有返回值，则ret返回值
 * 2.如果没有返回值，例如return; 或者压根没有写，则强行加一个ret
 * 对于读语句 -> GET_INT，num1为左值，其它全部缺省, especially,如果是数组，则num2为数组内位移
 * 对于写语句 -> PRINTF, num1为输出的字符串，其它缺省
 * 对于标签 -> LABEL，num1为标签值,num2，result缺省
 * 对于跳转语句 -> GOTO，num1为跳转的目标，其它缺省
 * 对于相等时转移/不等时转移/大小于时转移 -> BEQ,BNE,BGT,BLT,BLE,BGE, num1为第一个值，num2为第二个值，result为转移的标签，
 * 对于等于置1/不等于置1 -> SEQ/SNE, num1为第一个值，num2为第二个值，result为"AUTO_SEQ",若相等则返回1，否则为0
 * 对于小于置1/小于等于置1 -> SLT/SLE, num1为第一个值，num2为第二个值，result为"AUTO_SLT",符合条件置1
 * 对于辅助操作 -> POP_LAYER, ADD_LAYER, 全部缺省
 */