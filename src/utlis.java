import java.util.regex.Pattern;

public class utlis {
    public static boolean isInteger(String str) {
        Pattern pattern = Pattern.compile("^[-\\+]?[\\d]+$");
        return pattern.matcher(str).matches();
    }

    public static int calInteger(String num1, String num2, String op) {
        int intNum1 = Integer.parseInt(num1);
        int intNum2 = Integer.parseInt(num2);
        switch (op) {
            case "+":
                return intNum1 + intNum2;
            case "-":
                return intNum1 - intNum2;
            case "*":
                return intNum1 * intNum2;
            case "/":
                return intNum1 / intNum2; // 是否需要判断num2非0
            case "%":
                return intNum1 % intNum2;
        }
        System.out.println("error in utlis");
        return 0;
    }

    public static String two2one(String c, String x, String y) {
        String mid1, mid2;
        if (isInteger(c) && isInteger(x)) {
            mid1 = String.valueOf(calInteger(c, x, "*"));
        } else {
            mid1 = PseudoCodes.addTmp(OPType.MUL, c, x, "AUTO", GA.cur_func);
        }
        if (isInteger(mid1) && isInteger(y)) {
            mid2 = String.valueOf(calInteger(mid1, y, "+"));
        } else {
            mid2 = PseudoCodes.addTmp(OPType.ADD, mid1, y, "AUTO", GA.cur_func);
        }
        return mid2;
    }

    /**
     * 判断n是不是2的幂
     */
    public static boolean isPowerOfTwo(int n){
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * 返回 log2(n)
     * 要求必须得是2的幂
     */
    public static int log2(int n){
        return (int) (Math.log(n) / Math.log(2));
    }
}

//这个是在RelExp中调用的，两个addexp和一个中间的比较符号
class AddOpAdd{
    String R1;
    String R2;
    AddExp add1;
    AddExp add2;
    String op;

    public AddOpAdd(AddExp add1, AddExp add2, String R1, String R2, String op) {
        this.add1 = add1;
        this.add2 = add2;
        this.R1 = R1;
        this.R2 = R2;
        this.op = op;
    }
}