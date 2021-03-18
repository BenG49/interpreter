package compiler.parser.grammars.expressions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import compiler.Empty;
import compiler.exception.parse.*;
import compiler.exception.semantics.DuplicateIdException;
import compiler.exception.semantics.UnknownIDException;
import compiler.exception.CompileException;
import compiler.parser.Parser;
import compiler.parser.grammars.ast.*;
import compiler.semantics.FuncData;
import compiler.semantics.VarData;
import compiler.syntax.SymbolTable;
import compiler.syntax.Type;

public class Values {
    /**
     * vartypeliteral := VAR_TYPES
     */
    public static ASTNode<Empty, Type> VarTypeLiteral(Parser p) throws CompileException {
        Type out = p.l.nextType();
        p.eat(Type.getVarTypes());

        return new ASTNode<Empty, Type>(
            "VarTypeLiteral", new Empty(),
            out
        );
    }

    /**
     * returntypeliteral := VAR_TYPES
     *                    | VOID
     */
    public static ASTNode<Empty, Type> ReturnTypeLiteral(Parser p) throws CompileException {
        Type out = p.l.nextType();
        List<Type> temp = new ArrayList<Type>(Arrays.asList(Type.getVarTypes()));
        temp.add(Type.VOID);

        p.eat(temp);

        return new ASTNode<Empty, Type>(
            "ReturnTypeLiteral", new Empty(),
            out
        );
    }

    /**
     * truefalseliteral := TRUE
     *                   | FALSE
     */
    public static ASTNode<Empty, Type> TrueFalseLiteral(Parser p) throws CompileException {
        Type out = p.l.nextType();
        p.eat(Type.TRUE, Type.FALSE);

        return new ASTNode<Empty, Type>(
            "TrueFalseLiteral", new Empty(),
            out
        );
    }

    /**
     * stringliteral := STR
     */
    public static ASTNode<Empty, String> StringLiteral(Parser p) throws CompileException {
        return new ASTNode<Empty, String>(
            "StringLiteral", new Empty(),
            p.eat(Type.STR)
        );
    }

    /**
     * numberliteral := intliteral
     *                | floatliteral
     */
    public static ASTNode<?, ?> NumberLiteral(Parser p) throws CompileException {
        Type nextType = p.l.nextType();

        if (nextType == Type.INT)
            return IntLiteral(p);

        if (nextType == Type.FLOAT)
            return FloatLiteral(p);

        if (nextType == null)
            throw new EOFException("Operator");
        else
            throw new TokenTypeException(nextType, "NumberLiteral", p.l.getPos());
    }

    /**
     * intliteral := INT
     */
    public static ASTNode<Empty, Integer> IntLiteral(Parser p) throws CompileException {
        return new ASTNode<Empty, Integer>(
            "IntLiteral", new Empty(),
            Integer.parseInt(p.eat(Type.INT))
        );
    }

    /**
     * floatliteral := FLOAT
     */
    public static ASTNode<Empty, Float> FloatLiteral(Parser p) throws CompileException {
        return new ASTNode<Empty, Float>(
            "FloatLiteral", new Empty(),
            Float.parseFloat(p.eat(Type.FLOAT))
        );
    }

    /**
     * identifier := ID
     */
    public static ASTNode<Empty, String> Variable(Parser p, SymbolTable scopeTable) throws CompileException {
        return Variable(p, scopeTable, null);
    }
    public static ASTNode<Empty, String> Variable(Parser p, SymbolTable scopeTable, Type define) throws CompileException {
        String name = p.eat(Type.ID);
        boolean contains = scopeTable.vcontains(name);

        if (define == null && !contains)
            throw new UnknownIDException(p.l.getPos(), name);
        
        // special case, functions and vars can have the same name
        if (define != null && contains/*&& p.t.get(name).scope == scope*/)
            throw new DuplicateIdException(p.l.getPos(), name);
        
        if (define != null)
            scopeTable.vput(name, new VarData(define));

        return new ASTNode<Empty, String>("Identifier", new Empty(), name);
    }

    /**
     * function := FUNC
     */
    public static ASTNode<Empty, String> Function(Parser p, SymbolTable scopeTable) throws CompileException {
        String name = p.eat(Type.ID);
        if (!scopeTable.fcontains(name))
            throw new UnknownIDException(p.l.getPos(), name);

        return new ASTNode<Empty, String>("Function", new Empty(), name);
    }
    public static ASTNode<Empty, String> Function(Parser p, SymbolTable scopeTable, Type define, Type... args) throws CompileException {
        String name = p.eat(Type.ID);

        if (scopeTable.fcontains(name)/*&& p.t.get(name).scope == scope*/)
            throw new DuplicateIdException(p.l.getPos(), name);
        
        scopeTable.fput(name, new FuncData(define, args));
        
        return new ASTNode<Empty, String>("Function", new Empty(), name);
    }
    public static ASTNode<Empty, String> Function(Parser p, SymbolTable scopeTable, List<Type> define, List<Type> args) throws CompileException {
        String name = p.eat(Type.ID);

        if (scopeTable.fcontains(name)/*&& p.t.get(name).scope == scope*/)
            throw new DuplicateIdException(p.l.getPos(), name);
        
        scopeTable.fput(name, new FuncData(define, args));
        
        return new ASTNode<Empty, String>("Function", new Empty(), name);
    }

}
