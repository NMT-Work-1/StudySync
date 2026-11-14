# StudySync

StudySync is a web application built to help students break down study goals into smaller micro-tasks and fit them into their schedules. Users can type what they need to study into the chatbox, and the backend uses a ReAct (Reason-Act-Observe) agent loop to break the goal into subtasks, check open calendar times, and schedule study blocks.

The project runs on a Java Servlet backend (Apache Tomcat 9), a HTML/CSS/JavaScript frontend, and a MySQL database connected via JDBC.

## Database and Schema

We used **MySQL** for our database and **MySQL Connector/J (JDBC)** to connect it to our Java servlets. Since all of our data ties back to a specific user, a relational setup made it easy to link tasks, calendar events, and logs together.

Our setup script is in `Database Files/studysync_database.sql`, which creates the `studysync` database and five tables:

* **`users`**: Stores user login information (`user_id` as the primary key, `username` (unique), `email` (unique), and `password_hash`).
* **`tasks`**: Stores both main tasks and micro-tasks. It includes `task_id` (Primary Key), `user_id` (Foreign Key to `users(user_id)`), `title`, `description`, `due_date`, `status` (defaults to `'Pending'`), `is_immediate`, `duration_estimate` (in minutes), and `parent_task_id` (Foreign Key referencing `tasks(task_id)` so subtasks link back to their parent task).
* **`brain_dumps`**: Stores one autosaved scratchpad note per user. It has `dump_id` (Primary Key), `user_id` (marked `UNIQUE` and a Foreign Key to `users(user_id)` so each user only has one record that updates automatically), `content`, and `updated_at`.
* **`calendar_events`**: Stores scheduled calendar events for each user. It includes `event_id` (Primary Key), `user_id` (Foreign Key to `users(user_id)`), `task_id` (optional Foreign Key to `tasks(task_id)`), `title`, `start_time`, `end_time`, and `created_at`. It also has a composite index on `user_id`, `start_time`, and `end_time`.
* **`agent_logs`**: Logs every tool call made by the AI agent. It has `log_id` (Primary Key), `user_id` (Foreign Key to `users(user_id)`), `tool_name`, `input` (`JSON`), `output` (`JSON`), and `created_at`. It also has indexes on `user_id`, `tool_name`, and `created_at`.

## Backend Run Instructions

### 1. JDBC Connection Details
* **Host / URL:** `jdbc:mysql://localhost:3306/studysync`
* **Database Name:** `studysync`
* **JDBC Driver:** `com.mysql.cj.jdbc.Driver` (included in `pom.xml` via `mysql-connector-j`)
* **Database User:** Configured through the `DB_USER` environment variable.
* **Database Password:** Configured through the `DB_PASSWORD` environment variable.

The application reads its database connection settings from three environment variables:
* `DB_URL`
* `DB_USER`
* `DB_PASSWORD`

To create the database and load the sample data from the SQL file, run:
```bash
mysql -u root -p < "Database Files/studysync_database.sql"
```

### 2. Password and API Key Locations
* **Database Password:** Read from the `DB_PASSWORD` environment variable by `DatabaseConnection.java` (`com.studysync.database.DatabaseConnection`). The database URL and username are also read from the `DB_URL` and `DB_USER` environment variables. Set these variables to match your local MySQL configuration before running the application.
* **LLM API Key:** Read by `AgentServlet.java` from a `.gemini_key` file located in the user's home directory (`~/.gemini_key`) using `Paths.get(System.getProperty("user.home"), ".gemini_key")`.

To create the key file before running the server:
```bash
echo -n "YOUR_GEMINI_API_KEY" > ~/.gemini_key
```

### 3. Building and Running
The project uses Maven (`pom.xml`) for `Gson`, `OkHttp`, and `mysql-connector-j`, and runs on **Apache Tomcat 9**.
```bash
mvn clean package
```
Deploy the generated `.war` file from the /target folder to Tomcat 9 or run the project directly in NetBeans.

## Servlet Endpoints & Agent Tools

Our `AgentServlet` acts as the central orchestrator using a Gemini Flash model, while our three tool servlets handle specific task and calendar operations.

| Servlet Class | URL Endpoint | HTTP Method | Agent Tool / Role Implemented |
| :--- | :--- | :--- | :--- |
| `AgentServlet` | `/api/agent` | `POST` | **ReAct Orchestrator**: Runs the Thought-Action-Observation loop, communicates with the Gemini API via OkHttp, and dispatches calls to the tool endpoints. |
| `CreateMicroTasksServlet` | `/api/tasks/create` | `POST` | **`CREATE_MICROTASK`**: Accepts a JSON payload with a `parentTitle` and an array of `subtasks` (`title` and `durationEstimate` in minutes), saving them to the `tasks` table. |
| `QueryAvailabilityServlet` | `/api/calendar/free-time` | `GET` | **`QUERY_AVAILABILITY`**: Checks the user's schedule between startDate and endDate, within the daily availableFrom and availableUntil time range, for open time blocks that fit requiredMinutes. |
| `ScheduleEventServlet` | `/api/calendar/book` | `POST` | **`SCHEDULE_EVENT`**: Books a specific study block onto the calendar using form parameters (`title`, `startTime`, `endTime`, and optional `taskId`). |

The AgentServlet uses a Gemini Flash model through the Gemini API. The model can be configured to use Gemini 3.6 Flash or Gemini 3.5 Flash Lite, depending on API availability and request volume. If one model experiences high request volumes or becomes unavailable, the other can be used as an alternative.

## Team Contributions (Week 3)

**[Benjamin Gong]**
Focused on getting the `AgentServlet` up and running with the Gemini API and structuring 
the core ReAct (Plan-Act-Observe) loop. Spent a good amount of time debugging Tomcat 9 
thread management—specifically adding a `destroy()` hook to shut down lingering OkHttp 
background threads during hot reloads—and extending socket timeouts to handle free-tier 
LLM latency spikes. Also wired up the `~/.gemini_key` file reader, strict system instructions 
so Gemini outputs clean JSON (`title` and `duration`), and the frontend `fetch()` integration 
so the chat UI actually talks to the backend.

**[Janelle Vilando]**
Designed and implemented the MySQL database schema (`studysync_database.sql`), including 
the foreign key relationships for parent/subtasks and the `agent_logs` table for 
tracking AI tool usage. Built out the `DatabaseConnection` utility class and the three 
tool servlets (`CreateMicroTasksServlet`, `QueryAvailabilityServlet`, and 
`ScheduleEventServlet`), making sure all incoming JSON payloads and query parameters 
are strictly validated before running `PreparedStatement` queries. Also helped integrate 
the tool servlets with AgentServlet by keeping the tool payloads and duration estimates consistent 
across the backend.

**[Quang Nguyen]**
Worked on the frontend interface (`index.html`, `style.css`, and `style.js`) and 
connecting the user experience to the backend endpoints. Built out the interactive 
chatbox UI, the Brain Dump autosave workflow, and the task list display components 
so users can see their micro-tasks update in real time once the agent finishes its loop. 
Also helped test end-to-end payload formatting between the frontend forms and the servlet 
endpoints to get everything ready for our demo recording.

## AI Tools Disclosure

Following the course AI guidelines, we used **Google Gemini** and **ChatGPT** to help explain concepts, walk through errors, and assist with parts of the code and grammar:

* **Walking through errors & concepts:** Used Gemini to understand Tomcat thread errors (`WebappClassLoaderBase`), OkHttp socket timeouts, and how to structure a ReAct (Thought-Action-Observation) loop.
* **Code assistance:** Used Gemini to help with the OkHttp request builder, `Gson` parsing in `AgentServlet.java`, and the `fetch()` call in `style.js`. Used ChatGPT to assist with JSON parsing, input validation, and exception handling in the three agent tool servlets.
* **Grammar & documentation:** Helped proofread and format this `README.md` file, with titles, headings, and highlights.
