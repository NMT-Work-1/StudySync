package com.studysync.servlet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

@WebServlet("/api/agent/test")
public class AgentServlet extends HttpServlet {

    private static final String GEMINI_URL = String.join("",
            "https://",
            "generativelanguage.googleapis.com",
            "/v1beta/models/gemini-3.8-flash:generateContent?key="
    );

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String SYSTEM_INSTRUCTIONS = 
        "You are the StudySync task management agent. You run in a loop of Thought, Action, Action Input, and Observation.\n" +
        "At the end of your turn, you must output either an Action OR a Final Answer. Never both.\n" +
        "Available Actions:\n" +
        "- CREATE_MICROTASK: Save a new study task to the database.\n" +
        "Strict Output Format:\n" +
        "Thought: Explain your reasoning.\n" +
        "Action: CREATE_MICROTASK\n" +
        "Action Input: {\"title\": \"Exact task name\", \"duration\": 30}\n\n" +
        "If you have completed the user's request, use this format instead:\n" +
        "Thought: I have finished creating the tasks.\n" +
        "Final Answer: [Your message to the user]\n";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("text/plain;charset=UTF-8");
        
        // 1. Get API Key
        String apiKey;
        try {
            Path keyPath = Paths.get(System.getProperty("user.home"), ".gemini_key");
            apiKey = Files.readString(keyPath, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            response.getWriter().write("API Key missing");
            return;
        }

        // 2. Initialize the ReAct Loop
        String userRequest = "I need to study for my physics midterm for 1 hour. Break it into two tasks.";
        StringBuilder transcript = new StringBuilder();
        transcript.append(SYSTEM_INSTRUCTIONS).append("\nUser Request: ").append(userRequest).append("\n");

        int maxSteps = 4;
        int currentStep = 0;
        
        System.out.println("========== STARTING REACT LOOP ==========");

        // 3. The ReAct While-Loop
        while (currentStep < maxSteps) {
            System.out.println("\n--- Step " + (currentStep + 1) + " ---");
            
            // Send the entire transcript to Gemini
            String llmResponse = callGeminiApi(transcript.toString(), apiKey);
            if (llmResponse == null) {
                response.getWriter().write("Error communicating with Gemini.");
                return;
            }
            
            System.out.println("GEMINI OUTPUT:\n" + llmResponse);
            transcript.append(llmResponse).append("\n");

            // Exit Condition: AI is finished
if (llmResponse.contains("Action: CREATE_MICROTASK")) {
                String jsonInput = extractTextAfter(llmResponse, "Action Input:");

                try {
                    // Parse the exact title and duration
                    JsonObject taskJson = JsonParser.parseString(jsonInput).getAsJsonObject();
                    String title = taskJson.get("title").getAsString();
                    int duration = taskJson.get("duration").getAsInt();

                    // Execute the actual MySQL JDBC insert
                    boolean dbSuccess = saveMicroTaskToDatabase(title, duration);

                    if (dbSuccess) {
                        System.out.println("--> [DB SUCCESS] Saved Task: '" + title + "' (" + duration + " mins)");
                        transcript.append("Observation: Task successfully saved to database.\n");
                    } else {
                        System.out.println("--> [DB ERROR] Failed to save Task: '" + title + "'");
                        transcript.append("Observation: Database error. Failed to save.\n");
                    }
                } catch (Exception e) {
                    System.out.println("--> [ERROR] Failed to parse AI JSON: " + jsonInput);
                    transcript.append("Observation: Error parsing JSON. Ensure strict format.\n");
                }
            }
            else {
                transcript.append("Observation: Invalid Action.\n");
            }
            currentStep++;
        }
        
        response.getWriter().write("Agent reached max steps without a Final Answer.");
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

    private String extractTextAfter(String source, String marker) {
        int startIndex = source.indexOf(marker) + marker.length();
        int endIndex = source.indexOf('\n', startIndex);
        if (endIndex == -1) endIndex = source.length();
        return source.substring(startIndex, endIndex).trim();
    }

    
    private boolean saveMicroTaskToDatabase(String title, int duration) {
    // Adjust your database URL, username, and password accordingly
    String dbUrl = "jdbc:mysql://localhost:3306/studysync_db"; 
    String dbUser = "root";
    String dbPassword = "your_password";
    
    String sql = "INSERT INTO microtasks (title, duration) VALUES (?, ?)";
    
    try {
        // Load the MySQL JDBC Driver (Required for Tomcat)
        Class.forName("com.mysql.cj.jdbc.Driver");
        
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, title);
            pstmt.setInt(2, duration);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    } catch (ClassNotFoundException | SQLException e) {
        e.printStackTrace();
        return false;
    }
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
