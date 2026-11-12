-- StudySync database setup

create database if not exists studysync;

-- Select the database we will use for StudySync
use studysync;

-- Stores account info for each user
create table users (
	user_id int primary key auto_increment,
	username varchar(50) not null unique, 
	email varchar(100) not null unique,
	password_hash varchar(255) not null
);

-- Store tasks created by users
create table tasks (
	task_id int primary key auto_increment,
	user_id int not null,
	title varchar(150) not null,
	description text,
	due_date DATETIME, 
	status varchar(20) not null default 'Pending',
	is_immediate boolean not null default false,
	duration_estimate int null,
	parent_task_id int null,

	-- Links each task to the user who created it
	foreign key (user_id) references users(user_id)
	-- Links subtasks to their parent task
	foreign key (parent_task_id) references tasks(task_id)
);

-- Brain Dump: one autosaved record per user
-- Create a record only when the user starts typing
-- Update it automatically; delete it when the user clicks Clear
-- Load the saved text when the user returns
-- UNIQUE(user_id) prevents multiple Brain Dumps per user
create table brain_dumps (
	dump_id int primary key auto_increment,
	user_id int not null unique,
	content text not null,
	updated_at timestamp not null 
		default current_timestamp
		on update current_timestamp,
	foreign key (user_id) references users(user_id)
);

-- stores every tool call made by the ai agent
create table agent_logs (
    log_id int primary key auto_increment,
    user_id int not null,
    tool_name varchar(100) not null,
    input json,
    output json,
    created_at timestamp not null default current_timestamp,

    foreign key (user_id) references users(user_id),

    index idx_agent_logs_user (user_id),
    index idx_agent_logs_tool (tool_name),
    index idx_agent_logs_created (created_at)
);

-- stores calendar events scheduled by users
create table calendar_events (
	event_id int primary key auto_increment,
	user_id int not null,
	task_id int null,
	title varchar(255) not null,
	start_time datetime not null,
	end_time datetime not null,
	created_at timestamp not null default current_timestamp,
	
	foreign key (user_id) references users(user_id),
	foreign key (task_id) references tasks(task_id),
	
	index idx_calendar_user_time (user_id, start_time, end_time)
);

-- Sample data for testing

insert into users (username, email, password_hash)
values ('testuser', 'test@example.com', 'temporary');


insert into tasks (user_id, title, description, status, is_immediate)
values (
    1,
    'Finish StudySync backend',
    'Test task retrieval using JDBC',
    'Pending',
    true
);

insert into tasks (user_id, title, duration_estimate, parent_task_id)
values
    (1, 'Test task retrieval using JDBC', 30, 1),
    (1, 'Test agent tool endpoints', 45, 1);

insert into brain_dumps (user_id, content)
values (
    1,
    'Finish the database and connect the agent tools.'
);


insert into agent_logs (user_id, tool_name, input, output)
values (
    1,
    'create_micro_tasks',
    '{"goal": "Finish StudySync backend"}',
    '{"result": "Created 3 micro-tasks"}'
);

insert into calendar_events (user_id, task_id, title, start_time, end_time)
values (
    1,
    1,
    'Review StudySync backend',
    '2026-11-12 09:00:00',
    '2026-11-12 10:00:00'
);




