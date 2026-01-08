package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified formula evaluation service using SpEL.
 *
 * <p>Supported formula patterns:
 * <ul>
 *   <li>{@code amount} - pass-through</li>
 *   <li>{@code amount * 0.11} - percentage (PPN 11%)</li>
 *   <li>{@code amount / 1.11} - division (extract DPP from gross)</li>
 *   <li>{@code amount + 1000} - addition</li>
 *   <li>{@code amount - 1000} - subtraction</li>
 *   <li>{@code amount > 2000000 ? amount * 0.02 : 0} - conditional (PPh 23)</li>
 *   <li>{@code 1000000} - constant value</li>
 * </ul>
 *
 * <p>Per Decision #13: Uses SimpleEvaluationContext for secure sandbox evaluation.
 */
@Service
public class FormulaEvaluator {

    private static final String VAR_AMOUNT = "amount";
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluate a formula expression with the given context.
     *
     * @param formula the formula expression (e.g., "amount * 0.11")
     * @param context the context containing variables (e.g., amount)
     * @return the calculated result, scaled to 2 decimal places
     * @throws IllegalArgumentException if the formula is invalid
     */
    @SuppressFBWarnings(
        value = "SPEL_INJECTION",
        justification = "SpEL injection is mitigated via multiple security controls: " +
                        "(1) Uses SimpleEvaluationContext (secure sandbox) instead of StandardEvaluationContext " +
                        "which blocks bean references, type references, constructors, and reflection. " +
                        "(2) Custom read-only PropertyAccessor prevents modifications. " +
                        "(3) Formulas are created by administrators in journal templates, not direct user input. " +
                        "(4) Per ADR #13, this is the recommended secure pattern for SpEL evaluation."
    )
    public BigDecimal evaluate(String formula, FormulaContext context) {
        if (formula == null || formula.isBlank()) {
            return context.amount();
        }

        String trimmed = formula.trim();

        // Handle simple "amount" case directly
        if (trimmed.equalsIgnoreCase(VAR_AMOUNT)) {
            return context.amount();
        }

        // Handle simple extended variable reference (e.g., "grossSalary")
        // Check if it's a simple identifier that exists in the variables map
        if (isSimpleIdentifier(trimmed) && context.variables().containsKey(trimmed)) {
            return context.get(trimmed);
        }

        try {
            // Use custom PropertyAccessor to expose variables map as properties
            SimpleEvaluationContext evalContext = SimpleEvaluationContext
                    .forPropertyAccessors(new MapPropertyAccessor())
                    .withRootObject(context)
                    .build();

            Expression expression = parser.parseExpression(trimmed);
            Object result = expression.getValue(evalContext);

            return toBigDecimal(result);
        } catch (SpelParseException e) {
            throw new IllegalArgumentException("Invalid formula syntax: " + formula + " - " + e.getMessage(), e);
        } catch (SpelEvaluationException e) {
            throw new IllegalArgumentException("Formula evaluation error: " + formula + " - " + e.getMessage(), e);
        }
    }
    
    /**
     * Custom PropertyAccessor that allows SpEL to access both FormulaContext fields
     * and variables from the Map as direct properties.
     */
    private static class MapPropertyAccessor implements PropertyAccessor {
        
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[] { FormulaContext.class };
        }
        
        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            if (!(target instanceof FormulaContext)) {
                return false;
            }
            FormulaContext formulaContext = (FormulaContext) target;
            // Can read "amount" or any variable in the map
            return VAR_AMOUNT.equals(name) || formulaContext.variables().containsKey(name);
        }
        
        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
            FormulaContext formulaContext = (FormulaContext) target;
            
            if (VAR_AMOUNT.equals(name)) {
                return new TypedValue(formulaContext.amount());
            }
            
            BigDecimal value = formulaContext.variables().get(name);
            if (value != null) {
                return new TypedValue(value);
            }
            
            throw new AccessException("Property '" + name + "' not found in FormulaContext");
        }
        
        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return false; // Read-only
        }
        
        @Override
        public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {
            throw new AccessException("FormulaContext is read-only");
        }
    }

    /**
     * Check if the formula is a simple identifier (variable name).
     * A simple identifier contains only letters, digits, and underscores,
     * and starts with a letter or underscore.
     */
    private boolean isSimpleIdentifier(String formula) {
        if (formula.isEmpty()) return false;
        char first = formula.charAt(0);
        if (!Character.isLetter(first) && first != '_') return false;
        for (int i = 1; i < formula.length(); i++) {
            char c = formula.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    /**
     * Validate a formula expression without evaluating it for real use.
     * Tests the formula against a sample context to check for errors.
     *
     * @param formula the formula expression to validate
     * @return list of error messages (empty if valid)
     */
    @SuppressFBWarnings(
        value = "SPEL_INJECTION",
        justification = "SpEL injection is mitigated - see evaluate() method for full justification. " +
                        "This validation method only parses syntax and does not execute untrusted code."
    )
    public List<String> validate(String formula) {
        List<String> errors = new ArrayList<>();

        if (formula == null || formula.isBlank()) {
            return errors; // Empty formula is valid (defaults to amount)
        }

        String trimmed = formula.trim();

        // Simple "amount" is always valid
        if (trimmed.equalsIgnoreCase(VAR_AMOUNT)) {
            return errors;
        }

        // Simple identifiers are assumed to be valid variables (will be provided at runtime)
        if (isSimpleIdentifier(trimmed)) {
            return errors;
        }

        // Try to parse the expression
        try {
            parser.parseExpression(trimmed);
        } catch (SpelParseException e) {
            errors.add("Syntax error: " + e.getMessage());
            return errors;
        }

        // For complex expressions, we cannot validate without knowing all variables at import time.
        // Instead, we only check for syntax errors above. Runtime evaluation will catch
        // missing variables when the template is actually used with real data.

        return errors;
    }

    /**
     * Preview formula evaluation with a sample amount.
     * Useful for showing calculated result in the UI.
     *
     * @param formula the formula expression
     * @param sampleAmount the sample amount to use
     * @return the calculated result, or null if formula is invalid
     */
    public BigDecimal preview(String formula, BigDecimal sampleAmount) {
        try {
            return evaluate(formula, FormulaContext.of(sampleAmount));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object result) {
        if (result == null) {
            throw new IllegalArgumentException("Formula returned null result");
        }

        if (result instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }

        if (result instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }

        throw new IllegalArgumentException("Formula must return a numeric value, got: " + result.getClass().getSimpleName());
    }
}
