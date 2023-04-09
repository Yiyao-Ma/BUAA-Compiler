import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SymTable {
    static public ArrayList<SymItem> table = new ArrayList<>();
    static public ArrayList<Integer> layers = new ArrayList<>();
    static public int name_max;
    static public int whileCount = 0;
    static public int mainCount = 0;
    static public int intCount = 0;
    static public int voidCount = 0;
    static public int formatCount = 0;
    static public boolean firstCall = true; //在Pscode生成四元式后变为FALSE
    static public ArrayList<SymItem> record = new ArrayList<>();
    static public Map<String, ArrayList<SymItem>> foreverTable = new HashMap<String, ArrayList<SymItem>>() {
        {
            put("GLOBAL", new ArrayList<>()); //初始化为GLOBAL
        }
    };
    static public Iterator<SymItem> recordIterator;
    //record容器用来存完整的符号表

    static void clear() {
        recordIterator = record.iterator();
        table.clear();
        layers.clear();
        whileCount = 0;
        mainCount = 0;
        intCount = 0;
        voidCount = 0;
        formatCount = 0;
        firstCall = false;
    }

    /*
    static void add(Token tk, STIType stiType, DataType dataType) {
        SymItem newItem = new SymItem(tk.getToken(), stiType, dataType, 0, 0, 0, 0);
        table.add(newItem);
    }*/

    /**
     * 函数头的地址就是新的地址
     * 在compunit里面有重新初始化local_addr
     * 1.进入新的函数之前
     * 2.从一个函数出来之前
     */
    static void addFunName(Token tk, DataType dataType, int addr) {
        int pos = table.size() - 1;
        for (; pos >= 0; pos--) {
            if (table.get(pos).stiType == STIType.func && table.get(pos).name.equals(tk.getToken())) {
                Error.add(tk.getRow(), "b", "function name: " + tk.getToken() + " redefine, ");
                return;
            }
        }
        SymItem newItem = new SymItem(tk, addr, tk.getToken(), tk.getToken(), STIType.func, dataType, 0, 0, 0, 0, null);
        table.add(newItem);
        tk.setSymItem(newItem);
        if (firstCall) {
            record.add(newItem);
            foreverTable.put(tk.getToken(), new ArrayList<>());
        }
    }


    static SymItem getFunc(String funcName) {
        for (SymItem si : table) {
            if (si.stiType == STIType.func && si.name.equals(funcName)) {
                return si;
            }
        }
        System.out.println("oh no can't find a func named " + funcName);
        return null;
    }

    /**
     * 返回函数funcName的当前局部变量个数
     * 注意要算出数组的大小啊！
     * 返回的是大小，不*4
     */
    static int getLocalNum(String funcName) {
        int count = 0;
        for (SymItem si : table) {
            if (si.cur_func.equals(funcName) &&
                    (si.stiType == STIType.var || si.stiType == STIType.constant)) {
                count += si.size / 4;
            }
        }
        return count;
    }

    /**
     * 返回参数para是funcName的第几个参数，从1开始计数
     */
    static int indexInFunc(String para, String funcName) {
        SymItem func = getFunc(funcName);
        int index = 0;
        for (SymItem pa : func.paras) {
            index++;
            if (pa.name.equals(para)) {
                return index;
            }
        }
        System.out.println("oh no, para: " + para + " doesn't in function: " + funcName);
        return 0;
    }

    static void addConst(Token tk, int dim, String cur_func, int addr) { //TODO目前dim1和dim2设为0，之后需要考虑具体真实的值
        if (layers.size() > 0) {
            if (checkVarRedefine(tk) == -1) { //has problem
                return;
            }
        }
        SymItem newItem = new SymItem(tk, addr, cur_func, tk.getToken(), STIType.constant, DataType.int_, 0, dim, 0, 0, null);
        table.add(newItem);
        newItem.setSize(4);
        tk.setSymItem(newItem);
        if (firstCall) {
            record.add(newItem);
            foreverTable.get(cur_func).add(newItem);
        }
    }

    /**
     * 用来更新多维数组每一维度的值
     */
    static void updateConstDims(String constName, int dim1, int dim2) {
        for (int i = table.size() - 1; i >= 0; i--) {
            SymItem item = table.get(i);
            SymItem itemRecord = record.get(i);
            if (item.name.equals(constName) && item.stiType == STIType.constant) {
                item.updateDims(dim1, dim2);
                //itemRecord.updateDims(dim1,dim2);
                if (item.dim == 1) {
                    item.setSize(dim1 * 4);
                    //itemRecord.setSize(dim1*4);
                } else if (item.dim == 2) {
                    item.setSize(dim1 * dim2 * 4);
                    //itemRecord.setSize(dim1 * dim2 * 4);
                }
                break;
            }
        }
    }

    static void updateVarDims(String varName, int dim1, int dim2) {
        for (int i = table.size() - 1; i >= 0; i--) {
            SymItem item = table.get(i);
            if (item.name.equals(varName) && item.stiType == STIType.var) {
                item.updateDims(dim1, dim2);
                if (item.dim == 1) {
                    item.setSize(dim1 * 4);
                } else if (item.dim == 2) {
                    item.setSize(dim1 * dim2 * 4);
                }
                break;
            }
        }
    }

    static void updateParaDims(String paraName, String funcName, int dim2) {
        for (int i = table.size() - 1; i >= 0; i--) {
            SymItem item = table.get(i);
            if (item.name.equals(paraName) &&
                    item.stiType == STIType.para && item.cur_func.equals(funcName)) {
                item.updateDims(0, dim2);
                //不设置size，因为不知道一维
                //只有二维参数才会调用这个函数
                break;
            }
        }
    }

    static void assignConst(String constName, int value) {
        for (int i = table.size() - 1; i >= 0; i--) {
            if (table.get(i).name.equals(constName) && table.get(i).stiType == STIType.constant) {
                table.get(i).addValue(value);
                break;
            }
        }
    }

    static SymItem getSFirstNotFunc(String name) {
        for (int i = table.size() - 1; i >= 0; i--) {
            if (table.get(i).name.equals(name) && table.get(i).stiType != STIType.func) {
                return table.get(i);
            }
        }
        return null;
    }

    static SymItem getItemInForever(String func_name, String name) {
        ArrayList<SymItem> items = foreverTable.get(func_name);
        if (items == null) {
            System.out.println("in getItemInForever, can't find function");
            return null;
        }
        for (SymItem item : items) {
            if (item.name.equals(name)) {
                return item;
            }
        }
        System.out.println("in getItemInForever, can't find " + name + " in function " + func_name);
        return null;
    }

    static SymItem getConst(String constName) {
        for (int i = table.size() - 1; i >= 0; i--) {
            if (table.get(i).name.equals(constName) && table.get(i).stiType == STIType.constant) {
                return table.get(i);
            }
        }
        return null;
    }

    static SymItem getVar(String varName) {
        for (int i = table.size() - 1; i >= 0; i--) {
            if (table.get(i).name.equals(varName) && table.get(i).stiType == STIType.var) {
                return table.get(i);
            }
        }
        return null;
    }

    static void addVar(Token tk, int dim, String cur_func, int addr) {
        //find redefine error(b)
        if (layers.size() > 0) {
            if (checkVarRedefine(tk) == -1) { //has problem
                return;
            }
        }
        SymItem newItem = new SymItem(tk, addr, cur_func, tk.getToken(), STIType.var, DataType.int_, 0, dim, 0, 0, null);
        table.add(newItem);
        newItem.setSize(4);
        tk.setSymItem(newItem);
        if (firstCall) {
            record.add(newItem);
            foreverTable.get(cur_func).add(newItem);
        }
    }

    static void addPara(Token tk, int dim, String cur_func, int addr) {
        //添加参数的时候应该已经在新的一层了，因此它对应的函数名应该是layer里面记录的最后一个参数对应的table的下标
        SymItem newItem = new SymItem(tk, addr, cur_func, tk.getToken(), STIType.para, DataType.int_, 0, dim, 0, 0, null);
        if (layers.size() > 0) {
            int cur_layer = layers.get(layers.size() - 1);
            SymItem itemFunc = table.get(cur_layer - 1); //找到这个参数对应的函数名字
            for (int i = cur_layer; i < table.size(); i++) {
                if (table.get(i).name.equals(tk.getToken()) && table.get(i).stiType == STIType.para) {
                    Error.add(tk.getRow(), "b", "var or constant name: " + tk.getToken() + " redefine, ");
                    return;
                }
            }
            itemFunc.paras.add(newItem);
            itemFunc.paraNum++;
        }
        table.add(newItem);
        tk.setSymItem(newItem);
        if (firstCall) {
            record.add(newItem);
            foreverTable.get(cur_func).add(newItem);
        }
    }

    /**
     * 四处加层：
     * 1、CompUnit开始时
     * 2、函数定义时（函数名在旧层里）
     * 3、Main函数进入block之前
     * 4、Stmt中进入Block之前
     */
    static void addLayer() {
        layers.add(table.size()); //每多一层，layer多一个记录，记录的是下一层第一个元素的下标（从0开始
        if (firstCall) {
            PseudoCodes.add(OPType.ADD_LAYER, "DEFAULT", "DEFAULT", "DEFAULT");
        }

    }


    static void writeTable() {
        System.out.println("===============SymTable==================");
        for (SymItem item : table) {
            System.out.println(item.toString());
        }
        System.out.println("===============SymTable End==================");
    }

    static int checkVarRedefine(Token tk) {
        int cur_layer = layers.get(layers.size() - 1);
        for (int i = cur_layer; i < table.size(); i++) {
            if (table.get(i).name.equals(tk.getToken()) && table.get(i).stiType != STIType.func) {
                Error.add(tk.getRow(), "b", "var or constant name: " + tk.getToken() + " redefine, ");
                return -1;
            }
        }
        return 0;
    }

    static void popLayer() {
        int pop = layers.get(layers.size() - 1);
        //index->pop-1 is the funcName
        layers.remove(layers.size() - 1);
        while (table.size() != pop) {
            SymItem symbol = table.get(table.size() - 1);
            if ((symbol.stiType == STIType.var || symbol.stiType == STIType.constant)) {
                GA.local_addr = Memory.popTmp(GA.cur_func, symbol.size);
            }
            table.remove(table.size() - 1);
        }
        if (firstCall) {
            PseudoCodes.add(OPType.POP_LAYER, "DEFAULT", "DEFAULT", "DEFAULT");
        }
    }

    static int searchTable(Token tk, int flag) { //flag=1->lval, flag=2->unaryExp
        int pos = table.size() - 1;
        if (flag == 1) {
            while (pos >= 0) {
                //check all constant, var and para in this level
                if (table.get(pos).stiType != STIType.func && table.get(pos).name.equals(tk.getToken())) {
                    return table.get(pos).dim; //为了知道返回的参数是几维的
                }
                pos--;
            }
        } else {
            int a = 5;
            while (pos >= 0) {
                //check all constant, var and para in this level
                if (table.get(pos).stiType == STIType.func && table.get(pos).name.equals(tk.getToken())) {
                    //下面这个部分是返回这个函数类型，如果是int则为0，void则为-1
                    if (table.get(pos).dataType == DataType.int_) {
                        return 0;
                    } else {
                        return -1;
                    }
                }
                pos--;
            }
        }
        Error.add(tk.getRow(), "c", "use undefined ident: " + tk.getToken());
        //System.out.println("use undefined ident: "+tk.getToken());
        return -2;
    }

    static void checkConst(Token tk) {
        int pos = table.size() - 1;
        while (pos >= 0) {
            if (table.get(pos).name.equals(tk.getToken())) {
                if (table.get(pos).stiType == STIType.para || table.get(pos).stiType == STIType.var) {
                    return;
                } else if (table.get(pos).stiType == STIType.constant) {
                    Error.add(tk.getRow(), "h", tk.getToken() + " is a constant, can't change it's value");
                    return;
                }
            }
            pos--;
        }
    }

    /**
     * @param name 需要查的变量名称
     * @return const value if exist, else "WRONG"
     * searchConst，用来在符号表中查const值
     */
    static String searchConst(String name, int index, int dim) {
        int pos = table.size() - 1;
        while (pos >= 0) {
            SymItem item = table.get(pos);
            if (item.name.equals(name) && item.stiType != STIType.constant) {
                return "WRONG"; //防止把参数和int型的算进去
            }
            if (item.name.equals(name)) {
                if (item.dim == dim && item.value.size() > index) { //变量本身和引用是一个维数，包括0维，0维默认index为0
                    return String.valueOf(item.value.get(index));
                } else { //说明虽然存在这个变量，但是根本就不是一个纬度
                    return "WRONG";
                }
            }
            pos--;
        }
        return "WRONG";
    }

    static void checkRParam(Token tk, ArrayList<Integer> dims) { //检验实参, tk为函数
        for (SymItem item : table) {
            if (item.stiType == STIType.func && item.name.equals(tk.getToken())) { //找到这个同名函数
                if (dims.size() != item.paraNum) {
                    Error.add(tk.getRow(), "d", "the function " + tk.getToken() + " needs " +
                            item.paraNum + " para(s) but gets " + dims.size() + " para(s) instead");
                    return;
                }
                ArrayList<SymItem> paras = item.paras;
                for (int i = 0; i < item.paraNum; i++) {
                    if (dims.get(i) == -2) { //如果为-2，说明是未定义，则不考虑类型错误
                        continue;
                    }
                    if (paras.get(i).dim != dims.get(i)) {
                        Error.add(tk.getRow(), "e", "the function " + tk.getToken() + "'s para " +
                                "doesn't match real paras");
                        return;
                    }
                }
            }
        }
    }

    static void moveGlobalAddr(int space) {
        //todo 不知道record有没有跟着改变
        for (SymItem si : table) {
            if (si.cur_func.equals("GLOBAL")) {
                si.addr += space;
            }
        }
    }

    static void changeLocalAddr(String funcName, String localName, int newAddr) {
        int i = table.size() - 1;
        while (i >= 0) {
            SymItem si = table.get(i);
            if (si.name.equals(localName) && si.stiType != STIType.func && si.cur_func.equals(funcName)) {
                si.addr = newAddr;
                return;
            }
            i--;
        }
        System.out.println("change Local Addr failed!");
    }

    static void reAddLayer() {
        addLayer();
    }

    static void rePopLayer() {
        popLayer();
    }

    static SymItem reAddItem() {
        SymItem si = recordIterator.next();
        table.add(si);
        return si;
    }

}
