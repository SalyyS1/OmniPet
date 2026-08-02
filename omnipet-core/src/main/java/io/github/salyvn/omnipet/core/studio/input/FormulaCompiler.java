package io.github.salyvn.omnipet.core.studio.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FormulaCompiler {
    private FormulaCompiler() {}

    static CompiledFormula compile(String formula) {
        if (formula == null || formula.isBlank() || formula.length() > 256) {
            throw new IllegalArgumentException("formula is blank or too long");
        }
        Parser parser = new Parser(formula);
        Node root = parser.parse();
        return variables -> checked(root.evaluate(variables == null ? Map.of() : variables));
    }

    private interface Node {
        double evaluate(Map<String, Double> variables);
    }

    private static final class Parser {
        private final String text;
        private int position;
        private int depth;
        private int operations;

        private Parser(String text) {
            this.text = text;
        }

        private Node parse() {
            Node value = expression();
            skip();
            if (position != text.length()) throw error("unexpected trailing input");
            return value;
        }

        private Node expression() {
            Node value = term();
            while (true) {
                skip();
                if (accept('+')) value = binary(value, term(), '+');
                else if (accept('-')) value = binary(value, term(), '-');
                else return value;
            }
        }

        private Node term() {
            Node value = power();
            while (true) {
                skip();
                if (accept('*')) value = binary(value, power(), '*');
                else if (accept('/')) value = binary(value, power(), '/');
                else if (accept('%')) value = binary(value, power(), '%');
                else return value;
            }
        }

        private Node power() {
            Node value = unary();
            skip();
            return accept('^') ? binary(value, power(), '^') : value;
        }

        private Node unary() {
            skip();
            if (accept('+')) return unary();
            if (accept('-')) {
                Node operand = unary();
                return variables -> checked(-operand.evaluate(variables));
            }
            return primary();
        }

        private Node primary() {
            skip();
            if (++operations > 256) throw error("formula is too complex");
            if (accept('(')) {
                if (++depth > 32) throw error("formula nesting is too deep");
                Node value = expression();
                expect(')');
                depth--;
                return value;
            }
            if (position < text.length()
                    && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
                return number();
            }
            String name = identifier();
            if (name == null) throw error("expected a number, variable, or function");
            skip();
            if (accept('(')) return function(name);
            return variables -> {
                Double value = variables.get(name);
                if (value == null) throw error("missing sample for " + name);
                return checked(value);
            };
        }

        private Node number() {
            int start = position;
            while (position < text.length()
                    && (Character.isDigit(text.charAt(position)) || ".eE+-".indexOf(text.charAt(position)) >= 0)) {
                if ((text.charAt(position) == '+' || text.charAt(position) == '-') && position > start
                        && text.charAt(position - 1) != 'e' && text.charAt(position - 1) != 'E') break;
                position++;
            }
            try {
                double value = checked(Double.parseDouble(text.substring(start, position)));
                return ignored -> value;
            } catch (NumberFormatException failure) {
                throw error("invalid number");
            }
        }

        private Node function(String name) {
            List<Node> arguments = new ArrayList<>();
            skip();
            if (!accept(')')) {
                arguments.add(expression());
                while (true) {
                    skip();
                    if (!accept(',')) break;
                    if (arguments.size() >= 3) throw error("function has too many arguments");
                    arguments.add(expression());
                }
                expect(')');
            }
            int required = switch (name) {
                case "abs", "floor", "ceil", "round" -> 1;
                case "min", "max" -> 2;
                case "clamp" -> 3;
                default -> throw error("unsupported function " + name);
            };
            if (arguments.size() != required) throw error(name + " takes " + required + " argument(s)");
            return variables -> evaluateFunction(name, arguments, variables);
        }

        private Node binary(Node left, Node right, char operator) {
            return variables -> {
                double first = left.evaluate(variables);
                double second = right.evaluate(variables);
                return checked(switch (operator) {
                    case '+' -> first + second;
                    case '-' -> first - second;
                    case '*' -> first * second;
                    case '/' -> first / second;
                    case '%' -> first % second;
                    case '^' -> Math.pow(first, second);
                    default -> throw new IllegalStateException("unsupported formula operator");
                });
            };
        }

        private String identifier() {
            skip();
            if (position >= text.length()
                    || !(Character.isLetter(text.charAt(position)) || text.charAt(position) == '_')) return null;
            int start = position++;
            while (position < text.length()
                    && (Character.isLetterOrDigit(text.charAt(position)) || "_.".indexOf(text.charAt(position)) >= 0)) {
                position++;
            }
            return text.substring(start, position);
        }

        private boolean accept(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!accept(expected)) throw error("expected '" + expected + "'");
        }

        private void skip() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + position);
        }
    }

    private static double evaluateFunction(String name, List<Node> arguments, Map<String, Double> variables) {
        double first = arguments.get(0).evaluate(variables);
        double second = arguments.size() > 1 ? arguments.get(1).evaluate(variables) : Double.NaN;
        double third = arguments.size() > 2 ? arguments.get(2).evaluate(variables) : Double.NaN;
        return checked(switch (name) {
            case "abs" -> Math.abs(first);
            case "floor" -> Math.floor(first);
            case "ceil" -> Math.ceil(first);
            case "round" -> Math.round(first);
            case "min" -> Math.min(first, second);
            case "max" -> Math.max(first, second);
            case "clamp" -> Math.max(second, Math.min(first, third));
            default -> throw new IllegalStateException("unsupported formula function");
        });
    }

    private static double checked(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("formula produced a non-finite value");
        return value;
    }
}
