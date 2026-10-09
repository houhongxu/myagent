package dev.myagent.business.service.agent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Replace or extend these small tools with your own application services. */
public class AgentTools {
    @Tool(name = "current_time", description = "Return the current date/time in an IANA timezone, for example Asia/Shanghai or UTC.")
    public Map<String, String> currentTime(@ToolParam(description = "IANA timezone name") String timezone) {
        try {
            return Map.of("timezone", timezone, "time", ZonedDateTime.now(ZoneId.of(timezone)).toString());
        } catch (DateTimeException | NullPointerException exception) {
            return Map.of("error", "Invalid timezone. Use an IANA name such as Asia/Shanghai or UTC.");
        }
    }

    @Tool(name = "calculate", description = "Accurate decimal arithmetic. Operation is add, subtract, multiply or divide. Chain calls for multiple steps. Operands are plain decimal strings, not expressions.")
    public Map<String, String> calculate(String left, String operation, String right) {
        if (!decimal(left) || !decimal(right)) return Map.of("error", "Use decimal numbers with up to 30 integer and 12 fractional digits.");
        BigDecimal a = new BigDecimal(left);
        BigDecimal b = new BigDecimal(right);
        if (operation == null) return Map.of("error", "Choose add, subtract, multiply or divide.");
        try {
            BigDecimal result = switch (operation) {
                case "add" -> a.add(b);
                case "subtract" -> a.subtract(b);
                case "multiply" -> a.multiply(b);
                case "divide" -> a.divide(b, MathContext.DECIMAL128);
                default -> null;
            };
            if (result == null) return Map.of("error", "Choose add, subtract, multiply or divide.");
            return Map.of("result", result.stripTrailingZeros().toPlainString());
        } catch (ArithmeticException exception) {
            return Map.of("error", "Division by zero is undefined.");
        }
    }

    private boolean decimal(String value) {
        return value != null && value.matches("[-+]?\\d{1,30}(\\.\\d{1,12})?");
    }
}
