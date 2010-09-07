/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2004 Mark Richters, University of Bremen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

// $Id$

package org.tzi.use.uml.ocl.expr;

import java.util.List;

import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MOperationCall;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemException;
import org.tzi.use.uml.sys.ppcHandling.ExpressionPPCHandler;
import org.tzi.use.util.StringUtil;

/**
 * An operation defined by a class.
 *
 * @version     $ProjectVersion: 0.393 $
 * @author  Mark Richters
 */
public final class ExpObjOp extends Expression {
    private MOperation fOp;
    private Expression[] fArgs; // the arguments, first one is "receiver" object
    
    public ExpObjOp(MOperation op, Expression[] args) 
        throws ExpInvalidException
    {
        super(op.resultType(), args);
        fOp = op;
        fArgs = args;
        if (! args[0].type().isTrueObjectType() )
            throw new ExpInvalidException(
                                          "Target expression of object operation must have " +
                                          "object type, found `" + args[0].type() + "'.");

        // check for matching arguments
        VarDeclList params = fOp.paramList();
        if (params.size() != (args.length - 1) )
            throw new ExpInvalidException(
                                          "Number of arguments does not match declaration of operation `" +
                                          fOp.name() + "'. Expected " + params.size() + " argument(s), found " +
                                          (args.length - 1) + ".");

        for (int i = 1; i < args.length; i++)
            if (! args[i].type().isSubtypeOf(params.varDecl(i - 1).type()) )
                throw new ExpInvalidException(
                                              "Type mismatch in argument `" + params.varDecl(i - 1).name() + 
                                              "'. Expected type `" + params.varDecl(i - 1).type() + 
                                              "', found `" + args[i].type() + "'.");
    }
    
    public Value eval(EvalContext ctx) {
    	ctx.enter(this);
    	
    	Value result = UndefinedValue.instance;
    	
    	Value selfVal = fArgs[0].eval(ctx);
    	
    	if (selfVal.isUndefined() || 
    			!(selfVal instanceof ObjectValue)) {
    		
    		ctx.exit(this, result);
    		return result;
    	}
    	
    	MObject self = ((ObjectValue)selfVal).value();
    	
    	if ((isPre() && (self.state(ctx.preState()) == null)) ||
    			(!isPre() && (self.state(ctx.postState()) == null))) {
    		
    		ctx.exit(this, result);
    		return result;
    	}
    	
    	MOperation operation = 
    		self.cls().operation(fOp.name(), true);
    	
    	if (!operation.isCallableFromOCL()) {
    		throw new RuntimeException("Cannot call operation " + operation);
    	}
    	 	
    	List<String> parameterNames = operation.paramNames();
    	Value[] arguments = new Value[parameterNames.size()];
    	for (int i = 1; i < fArgs.length; ++i) {
    		arguments[i-1]= fArgs[i].eval(ctx);
    	}
    	
    	// this must be done _after_ all parameters have been evaluated, 
    	// since the parameter names could shadow values which are 
    	// needed for a later parameter (see test\t005.*) 
    	ctx.pushVarBinding("self", selfVal);
    	for(int i = 0; i < parameterNames.size(); ++i) {
    		ctx.pushVarBinding(
    				parameterNames.get(i), 
    				arguments[i]);
    	}
    	
    	MOperationCall operationCall = 
    		new MOperationCall(this, self, operation, arguments);
    	
    	operationCall.setPreferredPPCHandler(new ExpressionPPCHandler());
    	
    	MSystem system = ctx.postState().system();
    	
    	try {
    		try {
    			system.enterOperation(operationCall, false);
    		} catch (MSystemException e) {
    			throw new RuntimeException(e.getMessage());
    		}
    		
    		try {
    			if (operation.hasExpression()) {
    				result = operation.expression().eval(ctx);
    			} else if (operation.hasStatement()) {
    				result = 
						system.evaluateStatementInExpression(
								operation.getStatement());
    			}
    		} catch (Exception e) {
    			operationCall.setExecutionFailed(true);
    			throw new RuntimeException(e.getMessage());
    		} finally {
    			try {
    				system.exitOperation(
    						result,
    						true);
    			} catch (MSystemException e) {
    				throw new RuntimeException(e.getMessage());
    			}
    		}
    	} finally {
    		ctx.popVarBindings(fArgs.length);
	    	ctx.exit(this, result);
    	}
    	
    	return result;
    }

    /**
     * Evaluates expression and returns result value.
     */
    /*public Value eval(EvalContext ctx) {
        ctx.enter(this);
        Value res = UndefinedValue.instance;
        Value val = fArgs[0].eval(ctx);
        // if we don't have an object we can't call its operation
        if (! val.isUndefined() ) {
            // get object
            ObjectValue objVal = (ObjectValue) val;
            MObject obj = objVal.value();
            MObjectState objState = isPre() ? 
                obj.state(ctx.preState()) : obj.state(ctx.postState());
                
            if (objState != null ) {
                // the object's type may be a subtype of the declared
                // type. The operation may be redefined in this
                // subclass. We have to get the possibly redefined
                // operation.
                MClass cls = obj.cls();
                MOperation op = cls.operation(fOp.name(), true);

                EvalContext newCtx = ctx;
                if (op.expression() == null) {
                    //  TODO: Reuse existing eval context!
                    newCtx = new EvalContext(ctx.preState(), ctx.postState(), new VarBindings(), null);
                }
                
                int debugOldSize = newCtx.varBindings().getStackSize();
                Value debugOldResultVal = newCtx.varBindings().getValue("result");
                
                int stackSize = pushVarBindings(ctx, newCtx, objVal, op);
                
                if (op.expression() != null) {
                    Expression opExpr = op.expression();
                    res = opExpr.eval(newCtx);
                } else {
                	throw new RuntimeException(
                			"Unexpected non-query operation in OCL expression "
                			+ "(should have been caught by the compiler)");
                }

                if (op.hasScript()) {
                	res = op.evaluateScript(newCtx);
                	
                	if (res == null) {
                		res = UndefinedValue.instance;
                	}
                }
                
                popVarBindings(newCtx, stackSize);
                
                assert newCtx.varBindings().getStackSize() == debugOldSize;
                assert debugOldResultVal == newCtx.varBindings().getValue("result");
                
            }
        }
        ctx.exit(this, res);
        return res;
    }

    private void popVarBindings(EvalContext ctx, int oldStackSize) {
        while(ctx.varBindings().getStackSize() > oldStackSize)
            ctx.popVarBinding();
    }

    private int pushVarBindings(EvalContext oldCtx, EvalContext newCtx, ObjectValue self, MOperation op) {
        int oldStackSize = newCtx.varBindings().getStackSize();
        // evaluate arguments
        Value [] argValues = new Value[fArgs.length - 1];
        for (int i = 0; i < fArgs.length - 1; i++)
            argValues[i] = fArgs[i + 1].eval(oldCtx);

        // bind the argument values to the operation's
        // parameters
        VarDeclList params = op.paramList();
        for (int i = 0; i < fArgs.length - 1; i++) {
            VarDecl decl = params.varDecl(i);
            newCtx.pushVarBinding(decl.name(), argValues[i]);
        }

        // the operation's expression must be evaluated in context
        // of the target object. The "self" variable is bound to
        // the receiver object.
        newCtx.pushVarBinding("self", self);
        return oldStackSize;
    }*/

    public String toString() {
        return fArgs[0] + "." + fOp.name() + atPre() +
            "(" + StringUtil.fmtSeq(fArgs, 1, ", ") + ")";
    }
    
    public MOperation getOperation() {
        return fOp;
    }

    public Expression[] getArguments() {
        return fArgs;
    }

	@Override
	public boolean hasSideEffects() {
		if (fOp.hasSideEffects()) {
			return true;
		}
		
		for (Expression arg : fArgs) {
			if (arg.hasSideEffects()) {
				return true;
			}
		}
		
		return false;
	}
}
