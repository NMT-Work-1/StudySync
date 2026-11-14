package com.studysync.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
/* For different version of Tomcat
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
*/
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.FormBody;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.studysync.database.DatabaseConnection;

@WebServlet("/api/agent")
public class AgentServlet extends HttpServlet {

    private static final String GEMINI_URL = String.join("",
            "https://",
            "generativelanguage.googleapis.com",
            "/v1beta/models/gemini-3.5-flash-lite:generateContent?key="
    );

    private static final String APP_URL = "http://localhost:8080/StudySync";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private static final String SYSTEM_INSTRUCTIONS = 
        "You are the StudySync task management agent.\n" +
        "Your job is to help users manage their study goals, find free time, and schedule tasks.\n\n" +

        "You have three available tools:\n\n" +

        "1. CREATE_MICROTASK\n" +
        "Use this to break a study goal into smaller tasks.\n" +
        "Action: CREATE_MICROTASK\n" +
        "Action Input: {\"parentTitle\":\"Physics Midterm\",\"subtasks\":[{\"title\":\"Review notes\",\"durationEstimate\":30}]}\n\n" +

        "2. QUERY_AVAILABILITY\n" +
        "Use this to find available time blocks in the user's calendar.\n" +
        "Convert the requested duration into minutes.\n" +
        "Always use requiredMinutes for the duration.\n" +
        "Use availableFrom and availableUntil to define the daily availability window in HH:mm format.\n" +
        "Action: QUERY_AVAILABILITY\n" +
        "Action Input: {\"startDate\":\"2026-11-14\",\"endDate\":\"2026-11-14\",\"availableFrom\":\"09:00\",\"availableUntil\":\"21:00\",\"requiredMinutes\":120}\n\n" +

        "3. SCHEDULE_EVENT\n" +
        "Use this to schedule an event in the user's calendar.\n" +
        "Required fields: title, startTime, and endTime.\n" +
        "Optional field: taskId. Include it only when an existing task ID is known.\n" +
        "Use the date-time format YYYY-MM-DDTHH:mm:ss.\n" +
        "After receiving a successful scheduling result, provide a Final Answer. Do not schedule the same event again.\n\n" +

        "Choose the appropriate tool based on the user's request.\n" +
        "Return only one Action or one Final Answer per response.\n" +
        "Use the results from previous tool calls when deciding what to do next.\n" +
        "When a user asks to break down a goal, find available time, and schedule it, create the subtasks first, check calendar availability, and then schedule the study session.\n" +
        "Use actual task IDs and available time blocks returned by the tools. Do not invent values.\n" +
        "If a tool fails, explain the issue instead of claiming the action succeeded.\n" +
        "Do not repeat successful actions.\n" +
        "When finished, use: Final Answer: [message to the user]\n";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Check if the user is logged in
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\": false, \"error\": \"User not logged in\"}");
            return;
        }
        // Retrieve userId
        int userId = (Integer) session.getAttribute("userId");
        
        String userRequest = request.getParameter("goal");
    
        if (userRequest == null || userRequest.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
                    "{\"success\": false, \"error\": \"Goal is required\"}"
            );
            return;
        }
        
        // 1. Get API Key
        String apiKey;
        try {
            // For local testing: Retrieve the Gemini API key from an environment variable
            //apiKey = System.getenv("GEMINI_API_KEY");

            // For deployment: Retrieve the Gemini API key from a configuration file
            Path keyPath = Paths.get(System.getProperty("user.home"), ".gemini_key");
            apiKey = Files.readString(keyPath, StandardCharsets.UTF_8).trim();

            // Validate the retrieved API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new Exception("API Key is missing");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(
            "{\"success\": false, \"error\": \"API Key missing or unavailable\"}"
            );
            return;
        }

        // 2. Initialize the ReAct Loop
        StringBuilder transcript = new StringBuilder();
        transcript.append(SYSTEM_INSTRUCTIONS).append("\nUser Request: ").append(userRequest).append("\n");

        int maxSteps = 4;
        int currentStep = 0;
        
        System.out.println("========== STARTING REACT LOOP ==========");

        // 3. Run the ReAct loop until Gemini provides a final answer or the maximum number of steps is reached
        while (currentStep < maxSteps) {
            System.out.println("\n--- Step " + (currentStep + 1) + " ---");
            
            // Send the entire transcript to Gemini
            String llmResponse = callGeminiApi(transcript.toString(), apiKey);
            if (llmResponse == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write(
                    "{\"success\": false, \"error\": \"Error communicating with Gemini.\"}"
                );
                return;
            }
            
            System.out.println("GEMINI OUTPUT:\n" + llmResponse);
            transcript.append(llmResponse).append("\n");

            // Check whether Gemini has completed the task and provided its final answer
            if (llmResponse.contains("Final Answer:")) {
                String finalAnswer = extractTextAfter(llmResponse, "Final Answer:");

                JsonObject finalResult = new JsonObject();
                finalResult.addProperty("success", true);
                finalResult.addProperty("message", finalAnswer);

                response.getWriter().write(finalResult.toString());
                return;
            }

            // Execute the CREATE_MICROTASK tool requested by Gemini
            if (llmResponse.contains("Action: CREATE_MICROTASK")) {
                String jsonInput = extractTextAfter(llmResponse, "Action Input:");

                try {
                    // Parse the tool input returned by Gemini
                    JsonObject taskJson = JsonParser.parseString(jsonInput).getAsJsonObject();
                    // Send the task details to the microtask creation endpoint as JSON
                    RequestBody body = RequestBody.create(
                            taskJson.toString(),
                            MediaType.get("application/json; charset=utf-8")
                    );
                    // Forward the user's session cookie to maintain authentication
                    String cookie = request.getHeader("Cookie");

                    Request.Builder toolRequest = new Request.Builder()
                            .url(APP_URL + "/api/tasks/create")
                            .post(body);

                    if (cookie != null) {
                        toolRequest.addHeader("Cookie", cookie);
                    }
                    // Execute the tool request and record its input and result in the database
                    try (Response toolResponse = CLIENT.newCall(toolRequest.build()).execute()) {
                        String result = toolResponse.body() != null
                                ? toolResponse.body().string()
                                : "";
                        
                        logToolCall(userId, "CREATE_MICROTASK", taskJson.toString(), result);
                        // Return the tool result to Gemini as an observation
                        if (!toolResponse.isSuccessful()) {
                            transcript.append("Observation: Tool failed with status ")
                                      .append(toolResponse.code())
                                      .append(". Response: ")
                                      .append(result)
                                      .append("\n");
                        } else {
                            transcript.append("Observation: ")
                                      .append(result)
                                      .append("\n");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    transcript.append("Observation: Error executing CREATE_MICROTASK.\n");
                }
            }
            // Execute the QUERY_AVAILABILITY tool requested by Gemini
            else if (llmResponse.contains("Action: QUERY_AVAILABILITY")) {

                String jsonInput = extractTextAfter(llmResponse, "Action Input:");

                try {
                    JsonObject inputJson = JsonParser.parseString(jsonInput).getAsJsonObject();
                    
                    // Extract the date, time range, and required duration from Gemini's input
                    String startDate = inputJson.get("startDate").getAsString();
                    String endDate = inputJson.get("endDate").getAsString();
                    String availableFrom = inputJson.get("availableFrom").getAsString();
                    String availableUntil = inputJson.get("availableUntil").getAsString();
                    String requiredMinutes = inputJson.get("requiredMinutes").getAsString();
                    // Build the availability request using URL query parameters
                    String url = APP_URL + "/api/calendar/free-time"
                        + "?startDate=" + java.net.URLEncoder.encode(startDate, StandardCharsets.UTF_8)
                        + "&endDate=" + java.net.URLEncoder.encode(endDate, StandardCharsets.UTF_8)
                        + "&availableFrom=" + java.net.URLEncoder.encode(availableFrom, StandardCharsets.UTF_8)
                        + "&availableUntil=" + java.net.URLEncoder.encode(availableUntil, StandardCharsets.UTF_8)
                        + "&requiredMinutes=" + java.net.URLEncoder.encode(requiredMinutes, StandardCharsets.UTF_8);

                    // Forward the user's session cookie to maintain authentication
                    String cookie = request.getHeader("Cookie");

                    Request.Builder toolRequest = new Request.Builder()
                            .url(url)
                            .get();

                    if (cookie != null) {
                        toolRequest.addHeader("Cookie", cookie);
                    }

                    // Execute the availability request and record the tool call and result
                    try (Response toolResponse = CLIENT.newCall(toolRequest.build()).execute()) {

                        String result = toolResponse.body() != null
                                ? toolResponse.body().string()
                                : "";

                        logToolCall(userId, "QUERY_AVAILABILITY", inputJson.toString(), result);

                        // Return the available time blocks to Gemini as an observation
                        if (!toolResponse.isSuccessful()) {
                            transcript.append("Observation: Tool failed with status ")
                                      .append(toolResponse.code())
                                      .append(". Response: ")
                                      .append(result)
                                      .append("\n");
                        } else {
                            transcript.append("Observation: ")
                                      .append(result)
                                      .append("\n");
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    transcript.append("Observation: Error executing QUERY_AVAILABILITY.\n");
                }
            }

            // Execute the SCHEDULE_EVENT tool requested by Gemini
            else if (llmResponse.contains("Action: SCHEDULE_EVENT")) {

                String jsonInput = extractTextAfter(llmResponse, "Action Input:");

                try {
                    JsonObject inputJson = JsonParser.parseString(jsonInput).getAsJsonObject();
                    System.out.println("SCHEDULE_EVENT INPUT: " + inputJson);

                    // Build the form request using the event details
                    RequestBody body = new FormBody.Builder()
                        .add("title", inputJson.get("title").getAsString())
                        .add("startTime", inputJson.get("startTime").getAsString())
                        .add("endTime", inputJson.get("endTime").getAsString())
                        .build();
                    // Include the task ID only when it is provided
                    if (inputJson.has("taskId") && !inputJson.get("taskId").isJsonNull()) {
                    body = new FormBody.Builder()
                            .add("title", inputJson.get("title").getAsString())
                            .add("startTime", inputJson.get("startTime").getAsString())
                            .add("endTime", inputJson.get("endTime").getAsString())
                            .add("taskId", inputJson.get("taskId").getAsString())
                            .build();
                    }
                    // Forward the user's session cookie to maintain authentication
                    String cookie = request.getHeader("Cookie");

                    Request.Builder toolRequest = new Request.Builder()
                            .url(APP_URL + "/api/calendar/book")
                            .post(body);

                    if (cookie != null) {
                        toolRequest.addHeader("Cookie", cookie);
                    }

                    // Execute the scheduling request and record the tool call and result
                    try (Response toolResponse = CLIENT.newCall(toolRequest.build()).execute()) {

                        String result = toolResponse.body() != null
                                ? toolResponse.body().string()
                                : "";

                        logToolCall(userId, "SCHEDULE_EVENT", inputJson.toString(), result);

                        if (!toolResponse.isSuccessful()) {
                            transcript.append("Observation: Tool failed with status ")
                                      .append(toolResponse.code())
                                      .append(". Response: ")
                                      .append(result)
                                      .append("\n");
                        } else {
                        
                            JsonObject toolResult = JsonParser.parseString(result).getAsJsonObject();
                            // Tell Gemini whether scheduling succeeded so it can respond appropriately
                            if (toolResult.has("success") && toolResult.get("success").getAsBoolean()) {
                                transcript.append("Observation: ")
                                        .append(result)
                                        .append("\n")
                                        .append("The scheduling action succeeded. Do not schedule this event again. ")
                                        .append("Provide a Final Answer now.\n");
                            } else {
                                transcript.append("Observation: ")
                                        .append(result)
                                        .append("\n");
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    transcript.append("Observation: Error executing SCHEDULE_EVENT.\n");
                }
            }
            // Inform Gemini when it returns an action that is not supported
            else {
                transcript.append("Observation: Invalid Action.\n");
            }
            currentStep++;
        }
        // Return an error if Gemini does not provide a final answer within the allowed steps
        JsonObject errorResult = new JsonObject();
        errorResult.addProperty("success", false);
        errorResult.addProperty("error", "Agent reached max steps without a Final Answer.");
        
        response.getWriter().write(errorResult.toString());
    }

    // Helper: Safely builds the JSON payload and calls OkHttp
    private String callGeminiApi(String prompt, String apiKey) {
        try {
            // Build {"contents": [{"parts": [{"text": prompt}]}]} safely with Gson
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            JsonArray parts = new JsonArray();
            parts.add(textPart);
            JsonObject content = new JsonObject();
            content.add("parts", parts);
            JsonArray contents = new JsonArray();
            contents.add(content);
            JsonObject payload = new JsonObject();
            payload.add("contents", contents);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
            Request geminiRequest = new Request.Builder()
                    .url(GEMINI_URL + apiKey)
                    .post(body)
                    .build();

            try (Response geminiResponse = CLIENT.newCall(geminiRequest).execute()) {
                if (!geminiResponse.isSuccessful() || geminiResponse.body() == null) return null;
                
                // Parse the deeply nested text from Gemini's response
                JsonObject responseJson = JsonParser.parseString(geminiResponse.body().string()).getAsJsonObject();
                return responseJson.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Saves the tool call and its result in the database
    private void logToolCall(int userId, String toolName, String input, String output) {

        String sql = "INSERT INTO agent_logs (user_id, tool_name, `input`, `output`) "
                   + "VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.setString(2, toolName);
            statement.setString(3, input);
            statement.setString(4, output);

            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
     // Extracts the text following a specified marker
    private String extractTextAfter(String source, String marker) {
        int startIndex = source.indexOf(marker) + marker.length();
        int endIndex = source.indexOf('\n', startIndex);
        if (endIndex == -1) endIndex = source.length();
        return source.substring(startIndex, endIndex).trim();
    }

    @Override
    public void destroy() {
        if (CLIENT != null) {
            CLIENT.dispatcher().executorService().shutdown();
            CLIENT.connectionPool().evictAll();
        }
        super.destroy();
    }
}
