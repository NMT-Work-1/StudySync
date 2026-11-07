package com.studysync.servlet;

import java.io.IOException;
import java.io.BufferedReader;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.studysync.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/api/tasks/create")
public class CreateMicroTasksServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\": false, \"error\": \"User not logged in\"}");
                return;
        }
        int userId = (Integer) session.getAttribute("userId");
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Store values for database insertion
        String parentTitle;
        JsonArray jsonArray;

        // Read the JSON request from the agent
        try {
            BufferedReader reader = request.getReader();
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            
            //Check the parent task title
            if (!jsonObject.has("parentTitle")
                || jsonObject.get("parentTitle").isJsonNull()
                || !jsonObject.get("parentTitle").isJsonPrimitive()
                || !jsonObject.get("parentTitle").getAsJsonPrimitive().isString()) {

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"error\": \"Invalid parent task title\"}");
                return;
            }
            
            parentTitle = jsonObject.get("parentTitle").getAsString();

            if (parentTitle.trim().isEmpty()) {
                
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"error\": \"Invalid parent task title\"}");
                return;
            }
            
            //Check the subtasks array
            if (!jsonObject.has("subtasks") || jsonObject.get("subtasks").isJsonNull() 
                || !jsonObject.get("subtasks").isJsonArray()) {
                
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"error\": \"Invalid subtasks array\"}");
                return;
            }

            jsonArray = jsonObject.getAsJsonArray("subtasks");
           
            if (jsonArray.isEmpty()) {

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"error\": \"No subtasks provided\"}");
                return;
            }
            
            // Validate each subtask before saving
            for (JsonElement element: jsonArray) {
                if(!element.isJsonObject()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"success\": false, \"error\": \"Invalid subtask format\"}");
                    return;
                }  
                JsonObject subtask = element.getAsJsonObject();
                if (!subtask.has("title") || !subtask.has("durationEstimate")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"success\": false, \"error\": \"Missing required subtask fields\"}");
                    return;
                }
                if (subtask.get("title").isJsonNull() || subtask.get("durationEstimate").isJsonNull()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"success\": false, \"error\": \"Subtask fields cannot be null\"}");
                    return;
                }
                //Check  the subtask data types
                if (!subtask.get("title").isJsonPrimitive() || !subtask.get("title").getAsJsonPrimitive().isString()
                        || !subtask.get("durationEstimate").isJsonPrimitive() 
                        || !subtask.get("durationEstimate").getAsJsonPrimitive().isNumber()) {

                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"success\": false, \"error\": \"Invalid subtask data types\"}");
                    return;
                }
                
                String title = subtask.get("title").getAsString();
                int durationEstimate = subtask.get("durationEstimate").getAsInt();
                // Check that the title and duration are valid
                if (title.trim().isEmpty() || durationEstimate <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"success\": false, \"error\": \"Invalid subtask title or duration\"}");
                    return;
                }
            }
        } catch (Exception e) {
            // Handle invalid JSON input
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"error\": \"Invalid JSON input\"}");
            return;
        }
        
        // SQL statement used to insert both the parent task and its subtasks
        String sql = "INSERT INTO tasks (user_id, title, duration_estimate, parent_task_id) VALUES (?, ?, ?, ?)";

        // Initialize database resources so they can be closed in finally
        Connection connection = null;
        PreparedStatement statement = null;
        
        try {
            // Open a database connection and prepare the insert statement
            connection = DatabaseConnection.getConnection();
            statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            
            statement.setInt(1, userId);
            statement.setString(2, parentTitle);
            statement.setNull(3, java.sql.Types.INTEGER);
            statement.setNull(4, java.sql.Types.INTEGER);

            statement.executeUpdate();
            
            // Get the ID generated for the parent task
            int parentTaskId;

            try (java.sql.ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    parentTaskId = generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating parent task failed, no ID returned.");
                }
            }   
            
            // Store the generated IDs of all subtasks
            JsonArray subtaskIDs = new JsonArray(); 
            // Insert each subtask and link it to the parent task
            for (JsonElement element : jsonArray) {
                JsonObject subtask = element.getAsJsonObject();
                
                String title = subtask.get("title").getAsString();
                int durationEstimate = subtask.get("durationEstimate").getAsInt();
                
                statement.setInt(1, userId);
                statement.setString(2, title);
                statement.setInt(3, durationEstimate);
                statement.setInt(4, parentTaskId);

                statement.executeUpdate();
                
                // Get and store the ID generated for this subtask
                try (java.sql.ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        subtaskIDs.add(generatedKeys.getInt(1));
                    } else {
                        throw new SQLException("Creating subtask failed, no ID returned.");
                    }
                }   
            }
            
            // Build the JSON response containing the IDs of the inserted tasks
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("parentTaskId", parentTaskId);
            result.add("subtaskIds", subtaskIDs);

            response.getWriter().write(result.toString());
        } catch (Exception e) {
            // Return an error if a database operation fails
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\": false, \"error\": \"Database error\"}");
        } finally {
            // Close the database resources after the operation finishes
            try {
                if (statement != null) {
                    statement.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
