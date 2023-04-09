import java.util.ArrayList;

public class SymItem {
    Token tk;
    String name;
    STIType stiType;
    DataType dataType;
    int paraNum = 0; //参数个数
    int dim = 0; // 维度
    int dim1 = 0; //第一个维度的数据
    int dim2 = 0; //第二个维度的数据
    ArrayList<SymItem> paras;
    int addr;
    String cur_func;
    ArrayList<Integer> value = new ArrayList<>();
    int size = 0;

    public SymItem(Token tk, int addr, String cur_func, String name, STIType stiType, DataType dataType,
                   int paraNum, int dim, int dim1, int dim2, ArrayList<Integer> value) {
        this.tk = tk;
        this.addr = addr;
        this.cur_func = cur_func;
        this.name = name;
        this.stiType = stiType;
        this.dataType = dataType;
        this.paraNum = paraNum;
        this.dim = dim;
        this.dim1 = dim1;
        this.dim2 = dim2;
        this.paras = new ArrayList<>();
        this.value = value;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Token getTk() {
        return tk;
    }

    public void addValue(int value) {
        if (this.value == null) {
            this.value = new ArrayList<>();
        }
        this.value.add(value);
    }

    public void updateDims(int dim1, int dim2) {
        this.dim1 = dim1;
        this.dim2 = dim2;
    }

    public String toString() {
        return "name: " + name + " STIType: " + stiType + " DataType: " +
                dataType + " addr: " + addr + " cur_func: " + cur_func +
                " paraNum: " + paraNum + " dim: " + dim + " dim1: "+dim1+
                " dim2: "+dim2+ " paras: " + paras;
    }

}

enum STIType {
    constant, //常量
    var, //变量
    para, //参数
    func, //函数
    tmp, //中间变量
    invalid
}

enum DataType {
    int_,
    void_,
    invalid
}