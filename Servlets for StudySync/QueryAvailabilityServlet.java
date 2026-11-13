package com.studysync.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.studysync.database.DatabaseConnection;
import java.util.ArrayList;
import java.util.List;


@WebServlet("/api/calendar/free-time")
public class QueryAvailabilityServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
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
        // Retrieve the userId
        int userId = (Integer) session.getAttribute("userId");
 
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        String availableFrom = request.getParameter("availableFrom");
        String availableUntil = request.getParameter("availableUntil");
        String requiredMinutesParam = request.getParameter("requiredMinutes");
        
        // Check that all required parameters were provided
        if (startDate == null || endDate == null || requiredMinutesParam == null
                || availableFrom == null || availableUntil == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"error\": \"Missing required parameters\"}");
            return;
        }
        
        int requiredMinutes;
        // Convert required minutes to an integer
        try {
            requiredMinutes = Integer.parseInt(requiredMinutesParam);

            if (requiredMinutes <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"error\": \"Invalid required minutes\"}");
            return;
        }
        
        LocalDate start_date;
        LocalDate end_date; 
        // Validate the requested date range
        try {
            start_date = LocalDate.parse(startDate);
            end_date = LocalDate.parse(endDate);

            if (start_date.isAfter(end_date)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"error\": \"Start date cannot be after end date\"}");
                return;
            }
        } catch (DateTimeParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"error\": \"Invalid date format. Use YYYY-MM-DD\"}");
            return;
        }
        
        LocalTime availableStartTime;
        LocalTime availableEndTime;
        // Validate the availability window
        try {
            availableStartTime = LocalTime.parse(availableFrom);
            availableEndTime = LocalTime.parse(availableUntil);

            if (!availableStartTime.isBefore(availableEndTime)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"error\": \"Invalid availability window\"}");
                return;
            }

        } catch (DateTimeParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"error\": \"Invalid time format. Use HH:mm\"}");
            return;
        }
        
        // Retrieve events that overlap with the requested date range
        String sql = "SELECT start_time, end_time FROM calendar_events "
                + "WHERE user_id = ? "
                + "AND start_time < DATE_ADD(?, INTERVAL 1 DAY) "
                + "AND end_time > ? "
                + "ORDER BY start_time";
        
        // Initialize database resources so they can be closed in finally
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        
        try {
            // Open a database connection and prepare the SQL statement
            connection = DatabaseConnection.getConnection();
            statement = connection.prepareStatement(sql);
            
            statement.setInt(1, userId);
            statement.setString(2, endDate);
            statement.setString(3, startDate);
            
            resultSet = statement.executeQuery();
            List<LocalDateTime[]> busyTimeBlocks = new ArrayList<>();
            List<LocalDateTime[]> freeTimeBlocks = new ArrayList<>();
            
            while (resultSet.next()) {
                LocalDateTime busyStartTime = resultSet.getTimestamp("start_time").toLocalDateTime();
                LocalDateTime busyEndTime = resultSet.getTimestamp("end_time").toLocalDateTime();

                busyTimeBlocks.add(new LocalDateTime[]{busyStartTime, busyEndTime});
            }
            // Calculate available time for each requested day
            for (LocalDate currentDate = start_date; !currentDate.isAfter(end_date); 
                currentDate = currentDate.plusDays(1)) {
                
                LocalDateTime dayStart = currentDate.atTime(availableStartTime);
                LocalDateTime dayEnd = currentDate.atTime(availableEndTime);
                LocalDateTime currentDateTime = dayStart;

                for (LocalDateTime[] busyBlock : busyTimeBlocks) {

                    LocalDateTime busyStartTime = busyBlock[0];
                    LocalDateTime busyEndTime = busyBlock[1];

                    // Skip events outside the availability window
                    if (!busyEndTime.isAfter(dayStart) || !busyStartTime.isBefore(dayEnd)) {
                        continue;
                    }

                    // Check if there is available time before the event
                    if (busyStartTime.isAfter(currentDateTime)) {
                        LocalDateTime freeEnd = busyStartTime.isBefore(dayEnd) ? busyStartTime : dayEnd;

                        if (Duration.between(currentDateTime, freeEnd).toMinutes() >= requiredMinutes) {
                            freeTimeBlocks.add(new LocalDateTime[]{currentDateTime, freeEnd});
                        }
                    }
                    // Move current time past the event
                    if (busyEndTime.isAfter(currentDateTime)) {
                        currentDateTime = busyEndTime.isBefore(dayEnd) ? busyEndTime : dayEnd;
                    }

                    if (!currentDateTime.isBefore(dayEnd)) {
                        break;
                    }
                }

                // Check for available time after the last event
                if (currentDateTime.isBefore(dayEnd)) {

                    if (Duration.between(currentDateTime, dayEnd).toMinutes() >= requiredMinutes) {
                        freeTimeBlocks.add(new LocalDateTime[]{currentDateTime, dayEnd});
                    }
                }
            }
            // Build the JSON response
            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"availableBlocks\": [");

            for (int i = 0; i < freeTimeBlocks.size(); i++) {

                LocalDateTime[] block = freeTimeBlocks.get(i);

                if (i > 0) {
                    json.append(",");
                }

                json.append("{\"start\": \"")
                        .append(block[0])
                        .append("\", \"end\": \"")
                        .append(block[1])
                        .append("\"}");
            }

            json.append("]}");

            response.getWriter().write(json.toString());
            
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\": false, \"error\": \"Database error\"}");
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }

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
