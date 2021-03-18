package compiler.parser.grammars.expressions;

import java.util.ArrayList;
import java.util.List;

import compiler.Empty;
import compiler.exception.semantics.InvalidTypeException;
import compiler.exception.semantics.ReturnArgCountException;
import compiler.exception.CompileException;
import compiler.syntax.Type;
import compiler.parser.Parser;
import compiler.parser.grammars.ast.*;

public class Expressions {
    /**
     * program := statementlist
     */
    public static ASTNode<String, ASTNode<?, ?>> Program(Parser p) throws CompileException {
        return new ASTNode<String, ASTNode<?, ?>>(
            "Program", "",
            StatementList(p)
        );
    }

    /**
     * statementlist := statement...
     */
    public static ASTNode<String, ASTNode<?, ?>> StatementList(Parser p) throws CompileException {
        List<ASTNode<?, ?>> statements = new ArrayList<ASTNode<?, ?>>();
        while (p.l.hasNext()) {
            ASTNode<?, ?> temp = Statement(p);
            if (temp != null)
                statements.add(temp);
        }

        return new ASTNode<String, ASTNode<?, ?>>(
            "StatementList", "",
            statements
        );
    }

    /**
     * statement :=     ifstatement
     *                | declarestatement
     *                | assignstatement
     *                | whilestatement
     *                | forstatement
     *                | functioncall
     *                | functiondeclaration
     *                | ""
     *              NEWLINE
     */
    public static ASTNode<?, ?> Statement(Parser p) throws CompileException {
        ASTNode<?, ?> out;
        Type nextType = p.l.nextType();

        // ifstatement
        if (nextType == Type.IF)
            out = IfStatement(p);
        // functiondeclaration
        else if ((nextType == Type.VOID || nextType.within(Type.getVarTypes())) && p.l.nextType(3) == Type.LPAREN)
            out = FunctionDeclaration(p);
        // declarestatement
        else if (nextType.within(Type.getVarTypes()))
            out = DeclareStatement(p);
        else if (nextType == Type.ID) {
            if (p.l.nextType(2) == Type.LPAREN)
                // functioncall
                out = FunctionCall(p);
            else
                // assignstatement
                out = AssignStatement(p);
        }
        // whilestatement
        else if (nextType == Type.WHILE)
            out = WhileStatement(p);
        // forstatement
        else if (nextType == Type.FOR)
            out = ForStatement(p);
        else {
            p.eat(Type.NEWLINE);
            return null;
        }

        p.eat(Type.NEWLINE);

        return out;
    }
    
    /**
     * functiondeclaration :=     vartypeliteral
     *                          | VOID
     *                       FUNC
     *                            declarestatement...
     *                       RPAREN LB blockstatementlist RB
     */
    public static ASTNode<String, ASTNode<?, ?>> FunctionDeclaration(Parser p) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();

        out.add(Values.ReturnTypeLiteral(p));
        out.add(Values.Function(p, (Type)out.get(0).branches.get(0)));
        p.eat(Type.LPAREN);

        String name = (String)out.get(1).branches.get(0);

        Type nextType = p.l.nextType();
        while (nextType != Type.RPAREN) {
            ASTNode<Empty, Type> type = Values.VarTypeLiteral(p);
            ASTNode<Empty, String> var = Values.Variable(p, (Type)out.get(0).branches.get(0));

            out.add(new ASTNode<Type, ASTNode<?, ?>>(
                "DeclareStatement", Type.EQUAL,
                type, var
            ));

            nextType = p.l.nextType();
            if (nextType != Type.RPAREN)
                p.eat(Type.COMMA);
        }
        
        p.eat(Type.RPAREN);
        p.eat(Type.LB);
        // TODO: add support for returning tuples
        out.add(BlockStatementList(p, new Type[] {(Type)out.get(0).branches.get(0)}));
        p.eat(Type.RB);

        return new ASTNode<String, ASTNode<?, ?>>(
            "FunctionDeclaration", name,
            out
        );
    }
    
    /**
     * functioncall := FUNC 
     *                     binaryexpression
     *                   | stringliteral
     *                   | truefalseliteral
     *                   | variable
     *                 COMMA
     *                 ...
     *                 RPAREN
     */
    public static ASTNode<String, ASTNode<?, ?>> FunctionCall(Parser p) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();
        out.add(Values.Function(p));
        p.eat(Type.LPAREN);
        Type nextType = p.l.nextType();

        // TODO: check function type, add functions to vardata
        while (nextType != Type.RPAREN) {
            // variable
            if (nextType == Type.ID)
                out.add(Values.Variable(p));
            // stringliteral
            else if (nextType == Type.STR)
                out.add(Values.StringLiteral(p));
            // truefalseliteral
            else if (nextType.within(Type.TRUE, Type.FALSE))
                out.add(Values.TrueFalseLiteral(p));
            // binaryexpression
            else
                out.add(BinExp.BinaryExpression(p));
            
            nextType = p.l.nextType();
            if (nextType == Type.COMMA)
                p.eat(Type.COMMA);

            nextType = p.l.nextType();
        }
        p.eat(Type.RPAREN);

        return new ASTNode<String, ASTNode<?, ?>>(
            "FunctionCall", (String)out.get(0).branches.get(0),
            out
        );
    }

    /**
     * blockstatementlist := statement... RB
     */
    public static ASTNode<String, ASTNode<?, ?>> BlockStatementList(Parser p, Type[] returnType) throws CompileException {
        List<ASTNode<?, ?>> statements = new ArrayList<ASTNode<?, ?>>();
        Type nextType = p.l.nextType();
        while (nextType != Type.RB) {
            ASTNode<?, ?> temp;
            if (returnType != null && nextType == Type.RETURN)
                temp = ReturnStatement(p, returnType);
            else
                temp = Statement(p);

            if (temp != null)
                statements.add(temp);

            nextType = p.l.nextType();
        }

        return new ASTNode<String, ASTNode<?, ?>>(
            "BlockStatementList", "",
            statements
        );
    }

    /**
     * returnstatement := RETURN
     *                      ( binaryexpression
     *                      | truefalseliteral
     *                      | stringliteral
     *                      | variable
     *                      | functioncall
     *                        ""
     *                      | COMMA)...
     */
    public static ASTNode<Type, ASTNode<?, ?>> ReturnStatement(Parser p, Type[] returnType) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();
        p.eat(Type.RETURN);

        Type nextType = p.l.nextType();
        for (int var = 1; nextType != Type.NEWLINE; var++) {
            if (var > returnType.length)
                throw new ReturnArgCountException(p.l.getPos(), returnType.length, var);

            if (nextType.within(Type.TRUE, Type.FALSE)) {
                if (returnType[var-1] == Type.BOOL_ID)
                    out.add(Values.TrueFalseLiteral(p));
                else
                    throw new InvalidTypeException(p.l.getPos(), nextType, returnType[var-1]);
            } else if (nextType.within(Type.STR)) {
                if (returnType[var-1] == Type.STR_ID)
                    out.add(Values.StringLiteral(p));
                else
                    throw new InvalidTypeException(p.l.getPos(), nextType, returnType[var-1]);
            } else if (nextType.within(Type.ID)) {
                Type returned;
                ASTNode<?, ?> temp;
                if (p.l.nextType(2) == Type.LPAREN) {
                    temp = FunctionCall(p);
                    returned = p.f.get((String)((ASTNode<?, ?>)temp.branches.get(0)).branches.get(0)).type;
                } else {
                    temp = Values.Variable(p);
                    returned = p.v.get((String)temp.branches.get(0)).type;
                }
                
                if (returnType[var-1] != returned)
                    throw new InvalidTypeException(p.l.getPos(), (temp.name.equals("Function")?Type.FUNC:returned), returnType[var-1]);

                out.add(temp);
            } else {
                if (returnType[var-1].within(Type.INT_ID, Type.FLOAT_ID))
                    out.add(BinExp.BinaryExpression(p));
                else
                    throw new InvalidTypeException(p.l.getPos(), nextType, returnType[var-1]);
            }
            
            nextType = p.l.nextType();

            if (nextType != Type.NEWLINE) {
                p.eat(Type.COMMA);
                nextType = p.l.nextType();
            }
        }

        return new ASTNode<Type, ASTNode<?, ?>>(
            "ReturnStatement", Type.RETURN,
            out
        );
    }

    /**
     * whilestatement := WHILE LPAREN boolexpression RPAREN LB blockstatementlist RB
     */
    public static ASTNode<Type, ASTNode<?, ?>> WhileStatement(Parser p) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();

        p.eat(Type.WHILE);
        p.eat(Type.LPAREN);
        out.add(BoolExp.BoolExpression(p));
        p.eat(Type.RPAREN);
        p.eat(Type.LB);
        out.add(BlockStatementList(p, null));
        p.eat(Type.RB);

        return new ASTNode<Type, ASTNode<?, ?>>(
            "WhileExpression", Type.WHILE,
            out
        );
    }

    /**
     * forstatement := FOR LPAREN
     *                             declarestatement
     *                           | ""
     *                         COMMA
     *                             boolexpression
     *                           | ""
     *                         COMMA
     *                             assignstatement
     *                           | ""
     *                   | vartypeliteral variable IN iterable
     *                 RPAREN LB blockstatementlist RB
     */
    public static ASTNode<Type, ASTNode<?, ?>> ForStatement(Parser p) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();

        p.eat(Type.FOR);
        p.eat(Type.LPAREN);
        
        // TODO: add case for iterable
        Type nextType = p.l.nextType();
        if (nextType != Type.COMMA)
            out.add(DeclareStatement(p));

        p.eat(Type.COMMA);
        nextType = p.l.nextType();
        if (nextType != Type.COMMA)
            out.add(BoolExp.BoolExpression(p));

        p.eat(Type.COMMA);
        nextType = p.l.nextType();
        if (nextType != Type.RPAREN)
            out.add(AssignStatement(p));

        p.eat(Type.RPAREN);
        p.eat(Type.LB);
        out.add(BlockStatementList(p, null));
        p.eat(Type.RB);

        return new ASTNode<Type, ASTNode<?, ?>>(
            "ForStatement", Type.FOR,
            out
        );
    }

    /**
     * declarestatement := vartypeliteral variable
     *                         ""
     *                       | COMMA variable...
     *                       | EQUALS 
     *                             binaryexpression
     *                           | stringliteral
     *                           | truefalseliteral
     *                           | variable
     *                           | functioncall
     *                             ""
     */
    public static ASTNode<?, ?> DeclareStatement(Parser p) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();
        Type type = p.l.nextType();
        // type
        out.add(Values.VarTypeLiteral(p));
        out.add(Values.Variable(p, type));

        String name = (String)out.get(1).branches.get(0);

        Type nextType = p.l.nextType();
        if (nextType == Type.COMMA) {
            while (nextType != Type.NEWLINE) {
                p.eat(Type.COMMA);
                out.add(Values.Variable(p, type));
                nextType = p.l.nextType();
            }

            return new ASTNode<Type, ASTNode<?, ?>>(
                "DeclareStatement", Type.EQUAL,
                out);
        } else if (nextType == Type.NEWLINE)
            return new ASTNode<Type, ASTNode<?, ?>>(
                "DeclareStatement", Type.EQUAL,
                out);

        // EQUALS
        p.eat(Type.EQUAL);
        
        nextType = p.l.nextType();
        // stringliteral
        if (nextType == Type.STR) {
            if (type.equals(Type.STR_ID))
                out.add(Values.StringLiteral(p));
            else
                throw new InvalidTypeException(p.l.getPos(), Type.STR, type);
        }
        // truefalseliteral
        else if (nextType.within(Type.TRUE, Type.FALSE)) {
            if (type == Type.BOOL_ID)
                out.add(Values.TrueFalseLiteral(p));
            else
                throw new InvalidTypeException(p.l.getPos(), nextType, type);
        } else if (nextType == Type.ID) {
            ASTNode<?, ?> temp;
            String assignment;
            if (p.l.nextType(2) == Type.LPAREN) {
                temp = FunctionCall(p);
                assignment = (String)((ASTNode<?, ?>)temp.branches.get(0)).branches.get(0);
            } else {
                temp = Values.Variable(p);
                assignment = (String)temp.branches.get(0);
            }

            if (p.v.get(name).type != p.v.get(assignment).type)
                throw new InvalidTypeException(p.l.getPos(), p.v.get(assignment).type, p.v.get(name).type);
            
            out.add(temp);
        // binaryexpression
        } else
            out.add(BinExp.BinaryExpression(p));
        
        
        return new ASTNode<Type, ASTNode<?, ?>>(
            "DeclareStatement", Type.EQUAL,
            out
        );
    }

    /**
     * assignstatement := variable
     *                        EQUALS 
     *                            binaryexpression
     *                          | stringliteral
     *                          | truefalseliteral
     *                          | variable
     *                          | functioncall
     *                   | assignoperator
     *                        ""
     *                      | binaryexpression
     */
    public static ASTNode<Type, ASTNode<?, ?>> AssignStatement(Parser p) throws CompileException {
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();
        // variable
        out.add(Values.Variable(p));

        Type nextType = p.l.nextType();
        // EQUALS
        if (nextType == Type.EQUAL) {
            p.eat(Type.EQUAL);

            nextType = p.l.nextType();
            // stringliteral
            if (nextType == Type.STR)
                out.add(Values.StringLiteral(p));
            // truefalseliteal
            else if (nextType.within(Type.TRUE, Type.FALSE))
                out.add(Values.TrueFalseLiteral(p));
            else if (nextType == Type.ID) {
                ASTNode<?, ?> temp;
                String assignment;
                if (p.l.nextType(2) == Type.LPAREN) {
                    temp = FunctionCall(p);
                    assignment = (String)((ASTNode<?, ?>)temp.branches.get(0)).branches.get(0);
                } else {
                    temp = Values.Variable(p);
                    assignment = (String)temp.branches.get(0);
                }

                String varName = (String)out.get(0).branches.get(0);
                if (p.v.get(varName).type != p.v.get(assignment).type)
                    throw new InvalidTypeException(p.l.getPos(), p.v.get(assignment).type, p.v.get(varName).type);
                
                out.add(temp);
                
            } else
            // binaryexpression
                out.add(BinExp.BinaryExpression(p));
        } else {
            Type[] temp = Operators.AssignOperator(p);
            // temp list to add to manual addition node
            List<ASTNode<?, ?>> addNode = new ArrayList<ASTNode<?, ?>>();

            addNode.add(out.get(0));
            // +=
            if (temp[0] != temp[1])
                // x+=1 -> x=x+1
                addNode.add(BinExp.BinaryExpression(p));
            // ++
            else
                addNode.add(new ASTNode<Empty, Integer>("IntLiteral", new Empty(), 1));
            
            out.add(new ASTNode<Type, ASTNode<?, ?>>(
                "BinaryExpression", temp[0],
                addNode
            ));
        }
        
        return new ASTNode<Type, ASTNode<?, ?>>(
            "AssignStatement", Type.EQUAL,
            out
        );
    }

    /**
     * ifstatement := IF LPAREN boolexpression RPAREN LB blockstatementlist RB
     *                    ""
     *                  | ELSE LB blockstatementlist RB
     *                  | ELSE ifstatement
     */
    public static ASTNode<Type, ASTNode<?, ?>> IfStatement(Parser p) throws CompileException {
        final String name = "IfStatement";
        List<ASTNode<?, ?>> out = new ArrayList<ASTNode<?, ?>>();

        p.eat(Type.IF);
        p.eat(Type.LPAREN);
        out.add(BoolExp.BoolExpression(p));
        p.eat(Type.RPAREN);

        p.eat(Type.LB);
        out.add(BlockStatementList(p, null));
        p.eat(Type.RB);
        
        Type nextType = p.l.nextType();
        // ELSE
        if (nextType == Type.ELSE) {
            p.eat(nextType);

            nextType = p.l.nextType();
            // ifstatement
            if (nextType == Type.IF)
                out.add(IfStatement(p));
            // LB blockstatementlist RB
            else {
                p.eat(Type.LB);
                out.add(BlockStatementList(p, null));
                p.eat(Type.RB);
            }
        }
        
        return new ASTNode<Type, ASTNode<?, ?>>(
            name, Type.IF,
            out
        );
    }
    
}
