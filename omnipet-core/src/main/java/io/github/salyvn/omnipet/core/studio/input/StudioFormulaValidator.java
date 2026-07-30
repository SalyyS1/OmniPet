package io.github.salyvn.omnipet.core.studio.input;

import java.util.Map;

/** Small bounded arithmetic validator/evaluator; deliberately has no vendor dependency. */
public final class StudioFormulaValidator {
    private StudioFormulaValidator() {}

    public static double validate(String formula, Map<String, Double> samples, double minimum, double maximum) {
        if (formula == null || formula.isBlank() || formula.length() > 256) throw new IllegalArgumentException("formula is blank or too long");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) throw new IllegalArgumentException("formula bounds are invalid");
        double result = new Parser(formula, samples == null ? Map.of() : samples).parse();
        if (!Double.isFinite(result) || result < minimum || result > maximum) throw new IllegalArgumentException("formula sample is outside bounds");
        return result;
    }

    public static double validateBounded(String formula, Map<String, Double> samples, double minimum, double maximum) {
        return validate(formula, samples, minimum, maximum);
    }

    private static final class Parser {
        private final String text;
        private final Map<String, Double> samples;
        private int position;
        private int depth;
        private int operations;

        Parser(String text, Map<String, Double> samples) {
            this.text = text;
            this.samples = samples;
        }

        double parse() {
            double value = expression();
            skip();
            if (position != text.length()) throw error("unexpected trailing input");
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                skip();
                if (accept('+')) value += term();
                else if (accept('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            double value = power();
            while (true) {
                skip();
                if (accept('*')) value *= power();
                else if (accept('/')) value /= power();
                else if (accept('%')) value %= power();
                else return checked(value);
            }
        }

        private double power() {
            double value = unary();
            skip();
            if (accept('^')) value = Math.pow(value, power());
            return checked(value);
        }

        private double unary() {
            skip();
            if (accept('+')) return unary();
            if (accept('-')) return -unary();
            return primary();
        }

        private double primary() {
            skip();
            if (++operations > 256) throw error("formula is too complex");
            if (accept('(')) {
                if (++depth > 32) throw error("formula nesting is too deep");
                double value = expression();
                expect(')');
                depth--;
                return value;
            }
            if (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
                int start = position;
                while (position < text.length() && (Character.isDigit(text.charAt(position)) || ".eE+-".indexOf(text.charAt(position)) >= 0)) {
                    if ((text.charAt(position) == '+' || text.charAt(position) == '-') && position > start
                            && text.charAt(position - 1) != 'e' && text.charAt(position - 1) != 'E') break;
                    position++;
                }
                try { return checked(Double.parseDouble(text.substring(start, position))); }
                catch (NumberFormatException error) { throw error("invalid number"); }
            }
            String name = identifier();
            if (name == null) throw error("expected a number, variable, or function");
            skip();
            if (accept('(')) {
                double first = expression();
                double second = Double.NaN;
                double third = Double.NaN;
                skip();
                if (accept(',')) second = expression();
                skip();
                if (accept(',')) third = expression();
                expect(')');
                return function(name, first, second, third);
            }
            Double value = samples.get(name);
            if (value == null) throw error("missing sample for " + name);
            return checked(value);
        }

        private double function(String name, double first, double second, double third) {
            return switch (name) {
                case "abs" -> requireArgs(name, second, third, Math.abs(first));
                case "floor" -> requireArgs(name, second, third, Math.floor(first));
                case "ceil" -> requireArgs(name, second, third, Math.ceil(first));
                case "round" -> requireArgs(name, second, third, Math.round(first));
                case "min" -> requireTwo(name, second, third, Math.min(first, second));
                case "max" -> requireTwo(name, second, third, Math.max(first, second));
                case "clamp" -> requireThree(name, second, third, Math.max(second, Math.min(first, third)));
                default -> throw error("unsupported function " + name);
            };
        }

        private double requireArgs(String name, double second, double third, double value) {
            if (!Double.isNaN(second) || !Double.isNaN(third)) throw error(name + " takes one argument");
            return checked(value);
        }

        private double requireTwo(String name, double second, double third, double value) {
            if (Double.isNaN(second) || !Double.isNaN(third)) throw error(name + " takes two arguments");
            return checked(value);
        }

        private double requireThree(String name, double second, double third, double value) {
            if (Double.isNaN(second) || Double.isNaN(third)) throw error(name + " takes three arguments");
            return checked(value);
        }

        private String identifier() {
            skip();
            if (position >= text.length() || !(Character.isLetter(text.charAt(position)) || text.charAt(position) == '_')) return null;
            int start = position++;
            while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position)) || "_.".indexOf(text.charAt(position)) >= 0)) position++;
            return text.substring(start, position);
        }

        private boolean accept(char expected) {
            if (position < text.length() && text.charAt(position) == expected) { position++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!accept(expected)) throw error("expected '" + expected + "'");
        }

        private void skip() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }

        private double checked(double value) {
            if (!Double.isFinite(value)) throw error("formula produced a non-finite value");
            return value;
        }

        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at position " + position); }
    }
}
