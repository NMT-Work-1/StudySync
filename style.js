
"use strict";

// ========================================
// STUDYSYNC DASHBOARD
// Frontend prototype for Apache NetBeans
// ========================================

// 1. DOM HELPERS AND STORAGE

const $ = (selector) => document.querySelector(selector);
const STORAGE_KEY = "studysync-demo-v1";

function toISO(date) {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function dayOffset(days) {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return toISO(date);
}

function timeToMinutes(time) {
    const [hours, minutes] = time.split(":").map(Number);
    return hours * 60 + minutes;
}

function minutesToTime(minutes) {
    const h = String(Math.floor(minutes / 60)).padStart(2, "0");
    const m = String(minutes % 60).padStart(2, "0");
    return `${h}:${m}`;
}

function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, (char) => {
        const entities = {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        };
        return entities[char];
    });
}

// 2. INITIAL DEMO DATA

const initialState = {
    tasks: [
        {
            id: 1,
            title: "Review Algorithms Lecture",
            due: dayOffset(1),
            hours: 1,
            priority: "High",
            done: false
        },
        {
            id: 2,
            title: "Submit Networking Problem Set",
            due: dayOffset(2),
            hours: 2,
            priority: "High",
            done: false
        },
        {
            id: 3,
            title: "Prepare Chemistry Quiz Notes",
            due: dayOffset(3),
            hours: 1,
            priority: "Medium",
            done: false
        }
    ],

    events: [
        {
            id: "class",
            title: "Computer Networks Lecture",
            date: dayOffset(0),
            start: "10:00",
            end: "11:15",
            type: "Class"
        },
        {
            id: "work",
            title: "Part-time Work Shift",
            date: dayOffset(0),
            start: "16:00",
            end: "20:00",
            type: "Work"
        }
    ],

    notes: [],

    settings: {
        start: "09:00",
        end: "21:00"
    }
};

let state;

try {
    const saved = localStorage.getItem(STORAGE_KEY);
    state = saved ? JSON.parse(saved) : initialState;
} catch (error) {
    console.error("Failed to load saved data:", error);
    state = initialState;
}

function saveState() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function showToast(message) {
    const toast = $("#toast");
    toast.textContent = message;
    toast.classList.add("show");

    setTimeout(() => {
        toast.classList.remove("show");
    }, 2800);
}

// 3. AUTOMATIC SCHEDULING ENGINE
// Divide assignments into 30-minute blocks.
// Avoid overlapping existing classes and work.

function planSchedule() {
    // Remove previously generated study blocks.
    state.events = state.events.filter(
        event => event.type !== "Study"
    );

    const priorityRank = {
        High: 0,
        Medium: 1,
        Low: 2
    };

    const tasks = state.tasks
        .filter(task => !task.done)
        .sort((a, b) => {
            const deadlineOrder = a.due.localeCompare(b.due);

            if (deadlineOrder !== 0) {
                return deadlineOrder;
            }

            return priorityRank[a.priority] - priorityRank[b.priority];
        });

    let unscheduledBlocks = 0;

    for (const task of tasks) {
        let blocksRemaining = Math.ceil(task.hours * 2);

        for (
            let offset = 0;
            offset < 30 && blocksRemaining > 0;
            offset++
        ) {
            const date = dayOffset(offset);

            if (date > task.due) {
                break;
            }

            const start = timeToMinutes(state.settings.start);
            const end = timeToMinutes(state.settings.end);

            for (
                let minute = start;
                minute + 30 <= end && blocksRemaining > 0;
                minute += 30
            ) {
                const isBusy = state.events.some(event => {
                    if (event.date !== date) {
                        return false;
                    }

                    const eventStart = timeToMinutes(event.start);
                    const eventEnd = timeToMinutes(event.end);

                    return (
                        minute < eventEnd &&
                        minute + 30 > eventStart
                    );
                });

                if (!isBusy) {
                    state.events.push({
                        id: `study-${task.id}-${date}-${minute}`,
                        taskId: task.id,
                        title: task.title,
                        date: date,
                        start: minutesToTime(minute),
                        end: minutesToTime(minute + 30),
                        type: "Study"
                    });

                    blocksRemaining--;
                }
            }
        }

        unscheduledBlocks += blocksRemaining;
    }

    saveState();
    return unscheduledBlocks;
}

// 4. DASHBOARD RENDERING

function renderDashboard() {
    const unfinished = state.tasks
        .filter(task => !task.done)
        .sort((a, b) => a.due.localeCompare(b.due));

    $("#taskCount").textContent =
        `${unfinished.length} tasks remaining`;

    $("#currentTask").textContent =
        unfinished[0]?.title || "All caught up!";

    $("#todayLabel").textContent =
        new Date().toLocaleDateString("en-US", {
            weekday: "short",
            month: "short",
            day: "numeric"
        });

    renderSchedule();
    renderPriorities();
    renderNotes();
}

function renderSchedule() {
    const today = dayOffset(0);

    const events = state.events
        .filter(event => event.date === today)
        .sort((a, b) => a.start.localeCompare(b.start));

    const container = $("#scheduleList");

    if (events.length === 0) {
        container.innerHTML =
            '<p class="muted">No events scheduled today.</p>';
        return;
    }

    container.innerHTML = events.map(event => `
        <div class="schedule-item">
            <span class="schedule-dot"></span>

            <div class="schedule-detail">
                <strong>${escapeHTML(event.title)}</strong>
                <small>
                    ${escapeHTML(event.type)}
                    · ${escapeHTML(event.end)} end
                </small>
            </div>

            <span class="schedule-time">
                ${escapeHTML(event.start)}
            </span>
        </div>
    `).join("");
}

function renderPriorities() {
    const tasks = state.tasks
        .slice()
        .sort((a, b) => {
            return (
                Number(a.done) - Number(b.done) ||
                a.due.localeCompare(b.due)
            );
        })
        .slice(0, 6);

    const container = $("#priorityList");

    if (tasks.length === 0) {
        container.innerHTML =
            '<p class="muted">No tasks yet. Add your first assignment.</p>';
        return;
    }

    container.innerHTML = tasks.map(task => `
        <div class="priority-item ${task.done ? "done" : ""}">

            <input
                type="checkbox"
                data-complete="${task.id}"
                ${task.done ? "checked" : ""}
                aria-label="Complete ${escapeHTML(task.title)}"
            >

            <div class="name">
                ${escapeHTML(task.title)}
                <small>
                    Due ${escapeHTML(task.due)}
                    · ${task.hours}h
                </small>
            </div>

            <span class="priority-pill ${task.priority === "High" ? "high" : ""}">
                ${escapeHTML(task.priority)}
            </span>

            <button
                class="delete-btn"
                data-delete="${task.id}"
                aria-label="Delete task"
            >
                ✕
            </button>

        </div>
    `).join("");
}

function renderNotes() {
    $("#noteList").innerHTML = state.notes
        .slice(-3)
        .reverse()
        .map(note => `
            <div class="note">
                ${escapeHTML(note)}
            </div>
        `)
        .join("");
}

// 5. POMODORO FOCUS TIMER

let duration = 25 * 60;
let remaining = duration;
let running = false;
let timerInterval = null;

function updateTimer() {
    const minutes = Math.floor(remaining / 60);
    const seconds = remaining % 60;

    $("#timerText").textContent =
        `${String(minutes).padStart(2, "0")}:` +
        `${String(seconds).padStart(2, "0")}`;

    const progress = (remaining / duration) * 100;

    $("#timerRing").style.setProperty(
        "--progress",
        `${progress}%`
    );

    $("#startTimer").textContent =
        running ? "Ⅱ Pause" : "▶ Start";
}

function stopTimer() {
    running = false;
    clearInterval(timerInterval);
    timerInterval = null;
    updateTimer();
}

$("#startTimer").addEventListener("click", () => {
    if (running) {
        stopTimer();
        return;
    }

    running = true;
    updateTimer();

    timerInterval = setInterval(() => {
        remaining = Math.max(0, remaining - 1);
        updateTimer();

        if (remaining === 0) {
            stopTimer();
            showToast("Session complete! Take a break.");
        }
    }, 1000);
});

$("#breakTimer").addEventListener("click", () => {
    stopTimer();

    duration = 5 * 60;
    remaining = duration;

    $("#timerMode").textContent = "SHORT BREAK";
    updateTimer();
});

$("#resetTimer").addEventListener("click", () => {
    stopTimer();

    duration = 25 * 60;
    remaining = duration;

    $("#timerMode").textContent = "DEEP WORK";
    updateTimer();
});

// 6. TASK MANAGEMENT

$("#completeTask").addEventListener("click", () => {
    const task = state.tasks
        .filter(item => !item.done)
        .sort((a, b) => a.due.localeCompare(b.due))[0];

    if (!task) {
        showToast("No unfinished tasks");
        return;
    }

    task.done = true;

    planSchedule();
    renderDashboard();
    showToast("Task completed");
});

$("#addTask").addEventListener("click", () => {
    $("#taskForm").elements.due.value = dayOffset(2);
    $("#taskDialog").showModal();
});

$("#cancelTask").addEventListener("click", () => {
    $("#taskDialog").close();
});

$("#taskForm").addEventListener("submit", (event) => {
    event.preventDefault();

    const form = event.target;
    const data = new FormData(form);

    const title = String(data.get("title")).trim();
    const due = String(data.get("due"));
    const hours = Number(data.get("hours"));
    const priority = String(data.get("priority"));

    if (
        !title ||
        due < dayOffset(0) ||
        !Number.isFinite(hours) ||
        hours <= 0
    ) {
        showToast("Enter a valid deadline and effort");
        return;
    }

    state.tasks.push({
        id: Date.now(),
        title: title,
        due: due,
        hours: hours,
        priority: priority,
        done: false
    });

    const missed = planSchedule();

    renderDashboard();
    $("#taskDialog").close();
    form.reset();

    showToast(
        missed > 0
            ? "Task added, but some blocks could not fit"
            : "Assignment scheduled in 30-minute blocks"
    );
});

// Handle task deletion.
$("#priorityList").addEventListener("click", (event) => {
    const button = event.target.closest("[data-delete]");

    if (!button) {
        return;
    }

    const id = Number(button.dataset.delete);

    state.tasks = state.tasks.filter(task => task.id !== id);

    planSchedule();
    renderDashboard();
    showToast("Task deleted");
});

// Handle checkbox completion.
$("#priorityList").addEventListener("change", (event) => {
    if (!event.target.matches("[data-complete]")) {
        return;
    }

    const id = Number(event.target.dataset.complete);
    const task = state.tasks.find(item => item.id === id);

    if (task) {
        task.done = event.target.checked;
    }

    planSchedule();
    renderDashboard();
});

// 7. BRAIN DUMP

$("#saveNote").addEventListener("click", () => {
    const input = $("#brainInput");
    const value = input.value.trim();

    if (!value) {
        showToast("Write a note first");
        return;
    }

    state.notes.push(value);

    saveState();
    renderNotes();

    input.value = "";
    showToast("Note saved");
});

$("#categorizeBtn").addEventListener("click", () => {
    const input = $("#brainInput");
    const value = input.value.trim();

    if (!value) {
        showToast("Write a note first");
        return;
    }

    let category = "General note";

    if (/deadline|assignment|homework|quiz|exam|submit/i.test(value)) {
        category = "Academic task";
    } else if (/work|shift|job/i.test(value)) {
        category = "Work constraint";
    }

    input.value = `[${category}] ${value}`;

    showToast("Note categorized using demo rules");
});

// 8. AI AGENT COMMAND (RULE-BASED DEMO)

function addChatMessage(message, sender) {
    const element = document.createElement("div");

    element.className =
        sender === "user"
            ? "message user-message"
            : "message agent-message";

    element.textContent = message;

    $("#chat").appendChild(element);
    $("#chat").scrollTop = $("#chat").scrollHeight;
}

$("#agentForm").addEventListener("submit", (event) => {
    event.preventDefault();

    const input = $("#agentInput");
    const command = input.value.trim();

    if (!command) {
        return;
    }

    addChatMessage(command, "user");
    input.value = "";

    const lower = command.toLowerCase();

    // Command: reorganize schedule.
    if (/schedule|replan|reorganize|conflict|reschedule/i.test(lower)) {
        const missed = planSchedule();

        renderDashboard();

        if (missed > 0) {
            addChatMessage(
                `I rebuilt your study plan, but ${missed} ` +
                "half-hour blocks could not fit before deadlines.",
                "agent"
            );
        } else {
            addChatMessage(
                "I rebuilt your study plan around classes " +
                "and work shifts using available 30-minute blocks.",
                "agent"
            );
        }

        return;
    }

    // Command: create assignment.
    if (/task|assignment|homework|project|finish|submit/i.test(lower)) {
        const title = command
            .replace(
                /^(please\s+)?(add|create|finish|submit|plan)\s+/i,
                ""
            )
            .slice(0, 100);

        state.tasks.push({
            id: Date.now(),
            title: title,
            due: dayOffset(3),
            hours: 2,
            priority: "Medium",
            done: false
        });

        const missed = planSchedule();
        renderDashboard();

        addChatMessage(
            `Created "${title}" with four 30-minute ` +
            "study blocks and a default deadline of 3 days. " +
            (missed ? "Some blocks could not fit." : ""),
            "agent"
        );

        return;
    }

    addChatMessage(
        "I can create assignments or rebuild your schedule. " +
        'Try "Plan my algorithms project" or ' +
        '"Reorganize my schedule". ' +
        "This is a rule-based prototype, not a connected AI model.",
        "agent"
    );
});

// 9. SETTINGS

$("#settingsBtn").addEventListener("click", () => {
    $("#studyStart").value = state.settings.start;
    $("#studyEnd").value = state.settings.end;

    $("#settingsDialog").showModal();
});

$("#closeSettings").addEventListener("click", () => {
    $("#settingsDialog").close();
});

$("#saveSettings").addEventListener("click", () => {
    const start = $("#studyStart").value;
    const end = $("#studyEnd").value;

    if (timeToMinutes(end) <= timeToMinutes(start)) {
        showToast("End must be after start");
        return;
    }

    state.settings = {
        start: start,
        end: end
    };

    const missed = planSchedule();

    renderDashboard();
    $("#settingsDialog").close();

    showToast(
        missed > 0
            ? "Settings saved; some tasks could not be scheduled"
            : "Planning hours updated"
    );
});

// 10. THEME AND FOCUS MODE

$("#themeToggle").addEventListener("click", () => {
    document.body.classList.toggle("dark");
});

$("#focusMode").addEventListener("click", () => {
    const panels = [
        "#schedulePanel",
        "#dumpPanel",
        "#agentPanel"
    ];

    const isFocused =
        document.body.classList.toggle("focused");

    panels.forEach(selector => {
        $(selector).hidden = isFocused;
    });

    $(".dashboard").style.gridTemplateColumns =
        isFocused ? "1fr" : "";

    showToast(
        isFocused
            ? "Focus Mode enabled"
            : "Focus Mode disabled"
    );
});

// 11. SIDEBAR NAVIGATION

document.querySelectorAll(".nav").forEach(button => {
    button.addEventListener("click", () => {
        document.querySelectorAll(".nav").forEach(item => {
            item.classList.remove("active");
        });

        button.classList.add("active");

        const view = button.dataset.view;

        const sections = {
            Dashboard: null,
            Timeline: "#schedulePanel",
            "Brain Dump": "#dumpPanel",
            Agent: "#agentPanel"
        };

        const selector = sections[view];

        if (document.body.classList.contains("focused")) {
            document.body.classList.remove("focused");

            ["#schedulePanel", "#dumpPanel", "#agentPanel"]
                .forEach(id => {
                    $(id).hidden = false;
                });

            $(".dashboard").style.gridTemplateColumns = "";
        }

        if (selector) {
            $(selector).scrollIntoView({
                behavior: "smooth",
                block: "center"
            });
        } else {
            window.scrollTo({
                top: 0,
                behavior: "smooth"
            });
        }
    });
});

// 12. INITIALIZE DASHBOARD

planSchedule();
renderDashboard();
updateTimer();

console.log("StudySync Dashboard initialized successfully.");
