abstract class Root {
    abstract Root.RootType getType();

    public static enum RootType {
        CompUnit,
        ConstDecl,
        ConstDef,
        ConstInitVal,
        VarDecl,
        VarDef,
        InitVal,
        FuncDef,
        FuncType,
        MainFuncDef,
        FuncFParams,
        FuncFParam,
        Block,
        Stmt,
        Exp,
        Cond,
        LVal,
        PrimaryExp,
        Number,
        UnaryExp,
        UnaryOp,
        FuncRParams,
        MulExp,
        AddExp,
        RelExp,
        EqExp,
        LAndExp,
        LOrExp,
        ConstExp,
        BlockItem,
        Decl,
        Terminal
    }
}
