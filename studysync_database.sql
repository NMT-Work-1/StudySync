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
	is_immediate BOOLEAN NOT NULL DEFAULT FALSE,
	
	-- Links each task to the user who created it
	foreign key (user_id) references users(user_id)
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




