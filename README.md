# BUAA-Compiler
BUAA C compiler code.

### 编译技术实验设计文档(总)


----

#### 总体架构

此编译器用Java语言实现，每个部分设计一个大类，例如词法分析`lexicalAnalysis`,语法分析`GA`等。特别地，在语法分析中建立30个内部类，分别代表不同的非终结符。除此之外，还设计了一些小类来存储变量，如当词法分析读完结果传给语法分析程序时，该元素的symbol、token、指针位置pos与行列数都需要一并传给语法分析器，因此构造*Token*类来管理元素；工具类`utlis`存储一些全局都需要调用的工具函数；地址类`Memory`来存储地址等等。对于中间代码和目标代码生成各建一个类，分别名为`PseudoCodes`与`MipsGenerate`。

总体采用一遍扫描法，即程序仅对输入内容进行一次扫描。扫描过程中语法分析调用词法分析，出错时调用错误处理，语义分析与中间代码生成嵌套在语法分析之中。在目标代码生成过程中，重新扫描中间代码。

----

#### 词法分析

##### 最初设计

最初设计为将输入的内容作为一个长字符串raw，并用pos指针来记录当前词法分析读取到的位置，symbol为当前识别的单词类型，token则为当前读到的字符串。

在pos小于raw的长度时，循环调用*getToken*函数，并将symbol不为空的结果加入到返回列表中。

*getToken*函数负责提取字符串。首先调用*getChar*函数来得到当前pos指向的字符ch，并根据ch的不同种类来进入到不同的*if-else*语句中去(例如ch为数字、字母、特殊符号等)。而每一个*if-else*负责将根据种类的不同将字符串识别完(例如如果ch为数字，则应将ch读完并赋予symbol、token正确的值)。我们约定如果多读了字符应该调用*retract*函数来回退，以保证下次开始*getToken*之前ch在上次的最后一个位置。

*getChar*函数负责来移动pos位置并读取字符进入ch中。

*retract*函数负责回退，即将pos的值减一，使ch回到上一个字符位置。

##### 修改与提升

观察发现没必要对每个种类都写入*if-else*语句。例如‘+’、‘-'、‘;'、‘,'等**单个**符号可以列一个map，key为符号本身，value则为其对应的symbol值。直接判断ch是否为该map的key，这种操作可以节省代码量。同样的方法也使用于保留字的判断。

例如下面代码所示：

```java
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
```

为了帮助后期定位错误，可以在getChar和retract函数中记录行列值line与col，并建立Token类，来封装管理词法分析解读的name、symbol、line等信息，以便后续使用。

```java
public class Token {
    private String symbol;
    private String token;
    private int pos=0;
    private int row;
    private int col;

    public Token(String symbol, String token, int pos, int row, int col) {
        //constructor
    }
		//get-functions below
  	//.....
}

```

---

#### 语法分析

##### 最初设计

基于递归下降设计理念，对词法分析返回的结果进行进一步解读。为每种非终结符写一个函数来解析，例如*CompUnit*、*ConstDecl*、*VarDecl*等等。其中每个函数需要考虑到多种读入的可能并将所有情况都覆盖到。为了避免回溯，必要时需要采取预读策略，在有多个选择时多读几个Token来做出正确判断，判断结束后根据需要进行回退。

规定每一个函数的结束多读一Token，每一个函数的开始不再读Token。

计划将词法分析读到的内容存在*cur_lex*列表中，并维护一个point指针，记录当前读到了*cur_lex*的第几个元素(采用一遍读取，但当回退时可能出现不一致的情况)。维护Arraylist类型数组*ret*来存储输出内容，包括词法与语法部分。

*getSym*函数实现读取新Token的操作。其中需要注意，如果上一次进行过回退，则point值应小于*cur_lex*长度，此时不应再调用词法分析，其它情况调用词法分析。

*retract*函数实现回退操作，需要注意回退的同时还得删除ret中当前部分词法分析的内容。

##### 修改与提升

写代码中途意识到*Stmt*中难以快速辨别*LVal*和*Exp*，因为*LVal*本身也有可能是*Exp*。似乎唯一的方法就是判断之后有没有赋值符号‘=’。因此需要在读完一个函数后进行判断是否需要回溯。此时的回溯与普通*retract*不同，需要同时删掉ret中关于语法分析与词法分析的记录。因此设计新函数*skip*，可以在发现需要回溯后一步调回之前状态。

retract函数的具体操作：

```java
private void retract() {
  			//实现退读一个symbol
        point--;
        tk = cur_lex.get(point - 1);
        sym = tk.getSymbol();
        for (int i = ret.size() - 1; i >= 0; i--) {
            if (ret.get(i).charAt(0) != '<') { //词法分析的内容
                ret.remove(i);
                break;
            }
        }
    }
```

skip函数的具体操作：

```java
private void skip(Integer record, Integer retLoc) {
  			//实现退回到record、retLoc的位置
        point = record;
        tk = cur_lex.get(point - 1);
        sym = tk.getSymbol();
        while (ret.size() > retLoc) {
            ret.remove(ret.size() - 1);
        }
    }
```



----

#### 错误处理

##### 最初设计

建立错误类ErrorItem，其中存储错误码eid、行号line与具体信息info。再建立错误类Error，用来管理所有的错误，并设计添加、删除和打印等方法。

为了更好地找到错误，此次作业建立符号表。建立符号表项类SymItem，并建立静态类SymTable来管理符号表项。

计划每遇到一个错误，都存放在errors中，在程序运行完后一步输出到errors.txt文件中。整个过程与词法分析、语法分析共同一遍处理。

##### 修改与提升

**SymItem**类存储符号表项

```java
public class SymItem {
    String name;
    STIType stiType;
    DataType dataType;
    int paraNum = 0; //参数个数
    int dim = 0; // 维度
    int dim1 = 0; //第一个维度的数据
    int dim2 = 0; //第二个维度的数据
    ArrayList<SymItem> paras;
    boolean valid = true;
  	public SymItem(String name, STIType stiType, DataType dataType,
                   int paraNum, int dim, int dim1, int dim2){
      //constructor
    }
}
```

并将kind与type封装

```java
enum STIType {
    constant, //常量
    var, //变量
    para, //参数
    func //函数
}

enum DataType {
    int_,
    void_,
    invalid
}
```

**SymTable**类可以根据不同的需求添加符号表项。同时，为了满足程序需求，设计了mainCount/whileCount/intCount/voidCount/formatCount等变量，支持全局操作。

除此之外，维持一个layers数组，来存放层分界处的数组下标记录层数。

```java
public class SymTable {
    static public ArrayList<SymItem> table = new ArrayList<>();
    static public ArrayList<Integer> layers = new ArrayList<>();
    static public int whileCount = 0;
    static public int mainCount = 0;
    static public int intCount = 0;
    static public int voidCount = 0;
    static public int formatCount = 0;
  
  	static void addFunName(Token tk, DataType dataType){
      //addFunName
    }
  	
  	static void addConst(Token tk, int dim){
      //addConst
    }
  
  	static void addVar(Token tk, int dim){
      //addVar
    }
  
  	static void addPara(Token tk, int dim){
      //addPara
    }
  
  	static void addLayer(){
      //addLayer
    }
}
```

**Error**类来存储管理错误。

```java
public class Error {
    static public Map<Integer, String> errors = new TreeMap<>();
    static public Map<Integer, String> infos = new TreeMap<>();
    
  	static public void add(Integer line, String eid, String info){
    	//add
  	}
  
  	static public void skip(Set<Integer> errorLinesPre){
      //skip
    }
  
  	static public void showErrors(){
      //showErrors
    }
}

class ErrorItem{
    int line;
    String eid;
    String info;

    public ErrorItem(int line, String eid, String info) {
        this.line = line;
        this.eid = eid;
        this.info = info;
    }

}
```

在语法分析与词法分析程序中对Error进行调用，并在相应的位置报错，将错误信息存储到Error类中的errors容器中，最后输出到errors.txt中完成操作。

----

#### 代码生成

##### 最初设计

首先，代码生成部分可以分为两个阶段：

1. 源代码——>中间代码，即利用语义分析将输入的程序代码转换为四元式生成Pcode
2. 中间代码——>目标代码，即将Pcode四元式转化为Mips格式的目标代码

###### 中间代码

建立PseudoCode类封装四元式，存储四个元素，分别为 `op,num1,num2,result`。即运算法，左操作数，右操作数，结果。并用Arraylist进行存储中间代码。在语法分析的每个类中增加语义分析的内容，一遍生成中间代码(即从词法、语法、语义一共为一遍)。

根据官方文档的建议，对于 `A = B op C`的源代码翻译为 `op, B, C, #T1`, `=, A, #T1`

除此之外，对运算符op进行封装，将种类记录在`OpType`内。每一个运算符对应的num1, num2, result需要符合规则，具体规则定义如下：

```java
/**
 * OpType类型：
 *
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
```

###### 目标代码

中间代码生成后，重新遍历扫描中间代码生成目标代码。首先将字符串以`.asciiz`存在data区，全局变量数组也存在data区并赋予记号值，中间变量分配$t寄存器并进行合理分配，局部变量则存在内存中。

函数调用压栈出栈是生成mips的一大难题，我对函数调用的处理计划如下：

```
1. 先计算位移 ——> 20*4+B函数的参数个数*4+A函数目前的局部变量个数*4
2. 再填参数 ——> 参数填在： $sp - func_sp_offset + 4*paraNum
3. 移动指针 ——> $sp = $sp - func_sp_offset
4. 填寄存器 ——> 从下往上：$ra, $t0~$t9, $s0~$s7
5. 恢复寄存器
6. 指针移回
```

由于对于一个操作数来讲，有三种状态：在寄存器内、在内存里、是数字。因此在生成目标代码时应该做多重判断：

```
/* a=b+c:
 * abc都在寄存器/数字：                add a,b,c
 * ab在寄存器/数字，c在内存： lw reg2,c  add a,b,reg2
 * a在寄存器，bc在内存：               lw reg1,b  lw reg2,c  add a,reg1,reg2
 * a在内存，bc在寄存器/数字：           add reg1,b,c  sw reg1,a
 * ab在内存，c在寄存器/数字： lw reg1,b  add reg1,reg1,c  sw reg1,a
 * abc都在内存：                     lw reg1,b  lw reg2,c  add reg1,reg1,reg2  																		sw reg1,a
 */
```

在进行数组存取时，也需要判断操作数所在位置，例如取数组：

```
/* a=b[c]:
 * a在寄存器，b为全局：lw a,b(reg)
 * a在内存，b为全局： lw reg2,b(reg)  sw reg2, a
 * a在寄存器，b为局部：add reg,reg,offset  add reg,reg,$sp  lw a,0(reg)
 * a在内存，b为局部： add reg,reg,offset  add reg,reg,$sp  lw reg2,reg($sp)  sw reg2, a
 */
```

##### 修改与提升

在真正落笔去实现时，我还是发现实际有很多因素在最初没有考虑到。例如中间代码生成时面临着之前符号表结构不合适的问题，地址的记录问题，中间变量管理问题等；而目标代码生成则有涉及递归时函数压栈出栈的问题，函数调用数组部分传参的问题，与如符号表消失问题等等。在最后的生成阶段需要一一解决并进行修改提升。

###### 中间代码

+ 中间变量管理

  在涉及类似于`A = B op C op D op E`的运算操作时，需要先把结果存在中间变量里，再赋值给结果。为了更简单解决这个问题，我决定设计两个函数来添加中间代码, 第一个函数为直接生成四元式添加，而第二个函数会分配中间变量并将结果返回给调用函数者，并将中间变量相对于函数头的地址偏移记住。这样简化了语义分析中对中间变量分配的工作。

+ 符号表重构与常量传播

  为了将常量值记录在符号表里，我修改了符号表结构，增加一个数组来记录该表项的值。这样的好处在于不用区分纬度，0维则有一个值，1维两个，以此类推。

  在涉及到Exp时，层级寻找，如果最后在LVal中发现涉及的变量为常量，则从符号表中索取它的值返回数字，这样不仅简化代码，而且为后续优化铺路。

+ 记录while和if标签

  中间代码生成期间一个被忽视的难点是while和if的cond跳转问题。解决的办法是嵌套跳转。LOrExp中只要遇到一个为真则直接为真，而LAndExp中只要遇到一个为假则直接为假，EqExp、RelExp则可直接算出值。

  基于此，我给每一层传递两个参数：`successLabel`和`failLabel`，指明成功和失败需要跳转去哪里。如果其中某个为null，则代表成功/失败继续执行不跳转。

+ 地址记录

  建立`Memory`类来管理地址，对于全局变量、局部变量分别记录。全局变量从`DATA_BASE = 0x10010000`开始，而局部变量从`STACK_BASE = 0x7ffffffc`开始。	

  在反复的修改与提升后，我的中间代码类`PseudoCodes`定义如下：

```java
static public ArrayList<PseudoCode> midCodes = new ArrayList<>(); //存中间代码的表
static public ArrayList<tmpCode> tmpCodes = new ArrayList<>(); //存临时变量的表
static public Map<String, Integer> func_addr = new HashMap<>();
static public ArrayList<String> strcons = new ArrayList<>();
static public Map<String, Integer> lastUseTmp = new HashMap<>();

static public int code_count = 0;
static public int while_count = 0;
static public int if_count = 0;
static public int and_count = 0;
```

###### 目标代码

+ 递归函数压栈出栈

  最初的函数设计本质上是没有大问题的，但是由于涉及在push前算出位移，因此需要加一条命令：`PRE_CALL`来指示马上进入函数调用阶段。后续在用更严苛的样例评测中发现，我的栈记录最开始只设置为一个变量，当递归调用时会覆盖丢失之前的数据，从而出错。

  解决的办法是将sp移动的距离和参数个数存在栈里，每次压栈出栈，能保证连续调用也不会出错。

  ```java
  private LinkedList<Integer> func_sp_offset = new LinkedList<>(); 
  //函数开辟的栈的大小，是一个栈
  private LinkedList<Integer> paraNums = new LinkedList<>();
  ```

+ 符号表消失问题

  由于我在生成目标代码前的作业中都是一次遍历，因此从未留意过程序结束符号表消失的问题。但是当生成目标代码时需要单独遍历一次中间代码，此时发现符号表只剩全局变量了...

  解决方法：在生成目标代码途中模拟之前的方式重新建立符号表。生成目标代码前将除了符号表外的记录清空，并在中间代码遇到定义与赋值语句时在旧的符号表中查找表项加入到新的符号表里。这样可以保证中间代码途中符号表的状态与目标代码一致。

  ```java
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
  ```

+ 解决操作数多种状态

  操作数可能为数字、在寄存器内或是在内存。对于每种状态的存取方法不同，因此每次都需要判断。对于涉及多操作数的四元式，其组合情况繁多，会更加麻烦。

  解决方法：实现函数`name2addr`，使得无论是数字、寄存器还是内存的操作数都能返回其对应的地址。如果是数字就返回数字，在寄存器内就返回寄存器的值，在内存则返回其绝对地址或寄存器中存在地址。

  To be more specific, 对于非数字的操作数，当为部分数组传参时，在变量前加“~”作为标识。将数组地址存在`$a3`寄存器中返回，具体如下：

  ```
  //对于函数传数组，push的参数是没有经过处理的数组，例如 a[3]会是a~12, a[i]会是a~i*4
  //规定数组传参都传绝对地址,return回去的东西是我们要的地址本身
  //里面用到了$a2/$a3寄存器，前提是这块只有PUSH会用到，且保证PUSH不会用到$a2/$a3
  ```

最后，我的生成mips代码`MipsGenerate`定义变量如下：

```java
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
```

---

#### 代码优化

##### 最初设计

对于代码优化，我最开始有很多想法，包括全局优化、局部优化与表达式优化等，在阅读一些资料后计划的优化具体如下：

+ DAG优化
+ 函数内联展开
+ 循环展开
+ 常量传播
+ 乘除模优化
+ do-while优化
+ 数据流分析与寄存器分配
+ 窥孔优化
+ 针对mips语句优化
+ .....

###### 修改与提升

然而，在不懈的探索与实践中，我发现上述优化很多对竞速的6个测试样例不起作用，抑或是因为运行时间过长而TLE。因此最终真正做的优化如下：

+ DAG优化

  通过DAG可以消除公共子表达式并重新导出中间代码，虽然对我的编译器有百万级优化，但是无奈testfile2总是TLE。并且经过反复检查认为是代码循环次数过多、java运算时间过长导致的难以解决的问题。因此最后还是可惜地放弃了已经写好了的DAG优化。

+ 函数内联展开

  当函数体积不大且不涉及递归调用时可以通过用空间换时间的方法做函数内联展开，这样减少了调用函数前压栈出栈的语句。然而不幸的是，本次6个testfile似乎不涉及符合函数内联的代码。

+ 乘除模优化

  由于乘法的乘法系数为3，除法与模为100，因此做好乘除模优化会有很大的好处，具体如下：

  ```java
  /**
   * 乘法优化
   * a = b * c
   * 原理：
   * 1. 当一句乘法可以缩减成2句及以内的加法时，优化
   * 2. 当一句乘法里有2的幂时，可以用sll优化
   * 要求：传入的不能b和c都是数字
   */
  ```

  ```java
  /**
   * 除法优化
   * a = b / c
   * 原理：
   * 1. 当c为数字且为2的幂时，可以用sra。注意，若b为负数，会出现问题，因此需要通过跳转语句做判断
   * 2. 当b为0时，不用除，直接为0
   * 3. 当c为1时，不用除，直接 a=b
   * 4. 因为div $t1, $t2, $t3的语句为4条，因此展开成 div $t2, $t3, mflo $t1
   */
  ```

+ do-while优化

  对于这样的while循环：

  ```java
  while(a<b*b*b){ //or a<=b, a>b, a>=b, a==b, a!=b
    i = a + i;
    a = a + 1;
  }
  ```

  正常跳转的中间代码是这样的：

  ```label_cond: 
  label_cond:
  mul #t0, b, b
  mul #t1, #t0, b
  bge a, #t1, label_end
  add i, a, i
  addi a, a, 1
  goto label_cond
  label_end:
  ```

  这样的goto语句显得很没用，而且每次都要算b*b*b也显得很没用

  因此可以优化成do-while：

  ```
  mul #t0, b, b
  mul #t1, #t0, b
  bge a, #t1, label_end
  label_body:
  add i, a, i
  addi a, a, 1
  blt a, #t1, label_body
  label_end:
  ```

  看似只省去了goto一条语句，但其实在有1000次循环时就省去2500分乘法；看似少算了几次b\*b\*b，但只要做到循环内不出现就不算，那么节省开支将会巨大。

+ 窥孔优化

  对于一些比较小的点：

  a=b+0, a=b*1, a=b%0, a=b/1这种表达式进行特判等等。

+ 针对mips的优化

  根据观察，我发现对于结果相同的不同语句，mars会展开为不同的条数，例如

  ```
  因为div $t1, $t2, $t3的语句为4条，因此展开成 div $t2, $t3, mflo $t1
  在类似于 a = b + 1这种对常量的加法时，如果用addu则会有三条语句，如果用addiu只有一条
  ```

  虽然看似只减少了几条语句，但是嵌在递归函数或者是循环中红则能节省巨大开支。
