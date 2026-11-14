package com.studysync.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import com.studysync.database.DatabaseConnection;

@WebServlet("/api/calendar/book")
public class ScheduleEventServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Retrieve logged-in userId from session, or fallback to default test user (user_id = 1)
        HttpSession session = request.getSession(false);
        int userId = (session != null && session.getAttribute("userId") != null)
                ? (Integer) session.getAttribute("userId")
                : 1;

        String title = request.getParameter("title");
        String startTime = request.getParameter("startTime");
        String endTime = request.getParameter("endTime");
        String taskIdParam = request.getParameter("taskId");

        // Check required fields
        if (title == null || title.trim().isEmpty() || title.length() > 255
            || startTime == null || endTime == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
                    "{\"success\": false, \"message\": \"Missing required fields\"}"
            );
            return;
        }

        // Convert date/time values and validate them
        LocalDateTime start;
        LocalDateTime end;
        Integer taskId = null;

        try {
            start = LocalDateTime.parse(startTime.trim().replace(" ", "T"));
            end = LocalDateTime.parse(endTime.trim().replace(" ", "T"));

            if (taskIdParam != null && !taskIdParam.trim().isEmpty()) {
                taskId = Integer.parseInt(taskIdParam.trim());

                if (taskId <= 0) {
                    throw new NumberFormatException();
                }
            }

            if (!end.isAfter(start)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(
                        "{\"success\": false, \"message\": \"End time must be after start time\"}"
                );
                return;
            }

        } catch (DateTimeParseException | NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
                    "{\"success\": false, \"message\": \"Invalid date, time, or task ID\"}"
            );
            return;
        }

        // SQL statement for inserting the event into the database
        String sql = "INSERT INTO calendar_events "
                + "(user_id, task_id, title, start_time, end_time) "
                + "VALUES (?, ?, ?, ?, ?)";

        // Initialize database resources so they can be closed in finally
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = DatabaseConnection.getConnection();
            // Check if the task belongs to the logged-in user
            if (taskId != null) {
                String checkSql = "SELECT task_id FROM tasks WHERE task_id = ? AND user_id = ?";

                PreparedStatement checkStatement = null;
                ResultSet resultSet = null;

                try {
                    checkStatement = connection.prepareStatement(checkSql);
                    checkStatement.setInt(1, taskId);
                    checkStatement.setInt(2, userId);

                    resultSet = checkStatement.executeQuery();

                    if (!resultSet.next()) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write(
                                "{\"success\": false, \"message\": \"Task not found\"}"
                        );
                        return;
                    }

                } finally {
                    if (resultSet != null) {
                        resultSet.close();
                    }

                    if (checkStatement != null) {
                        checkStatement.close();
                    }
                }
            }
            statement = connection.prepareStatement(sql);

            statement.setInt(1, userId);

            if (taskId == null) {
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setInt(2, taskId);
            }

            statement.setString(3, title);
            statement.setTimestamp(4, Timestamp.valueOf(start));
            statement.setTimestamp(5, Timestamp.valueOf(end));

            int rowsInserted = statement.executeUpdate();

            if (rowsInserted > 0) {
                response.getWriter().write(
                        "{\"success\": true, \"message\": \"Event scheduled successfully\"}"
                );
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write(
                        "{\"success\": false, \"message\": \"Event could not be scheduled\"}"
                );
            }

        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(
                    "{\"success\": false, \"message\": \"Database error\"}"
            );
            e.printStackTrace();

        } finally {
            // Close database resources
            try {
                if (statement != null) {
                    statement.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
