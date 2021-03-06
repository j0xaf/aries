/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIESOR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.jpa.example.tasklist.blueprint.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.aries.jpa.example.tasklist.model.Task;
import org.apache.aries.jpa.example.tasklist.model.TaskService;
import org.osgi.service.component.annotations.Reference;

public class TasklistServlet extends HttpServlet {
    private transient TaskService taskService; // NOSONAR
    private static final long serialVersionUID = 34992072289535683L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
        IOException {
        String add = req.getParameter("add");
        String taskId = req.getParameter("taskId");
        String title = req.getParameter("title");
        PrintWriter writer = resp.getWriter(); // NOSONAR
        if (add != null) {
            addTask(taskId, title);
        } else if (taskId != null && taskId.length() > 0) {
            showTask(writer, taskId);
        } else {
            showTaskList(writer);
        }
    }

    private void addTask(String taskId, String title) {
        Task task = new Task();
        task.setId(new Integer(taskId));
        task.setTitle(title);
        taskService.addTask(task );
    }

    private void showTaskList(PrintWriter writer) {
        writer.println("<h1>Tasks</h1>");
        Collection<Task> tasks = taskService.getTasks();
        for (Task task : tasks) {
            writer.println("<a href=\"?taskId=" + task.getId() + "\">" + task.getTitle() + "</a><BR/>");
        }
    }

    private void showTask(PrintWriter writer, String taskId) {
        SimpleDateFormat sdf = new SimpleDateFormat();
        Task task = taskService.getTask(new Integer(taskId));
        if (task != null) {
            writer.println("<h1>Task " + task.getTitle() + " </h1>");
            if (task.getDueDate() != null) {
                writer.println("Due date: " + sdf.format(task.getDueDate()) + "<br/>");
            }
            writer.println(task.getDescription());
        } else {
            writer.println("Task with id " + taskId + " not found");
        }

    }

    @Reference
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

}
