<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <meta charset="UTF-8" />
    <title>Liste des employés</title>
</head>
<body>
<h1><%= request.getAttribute("message") %></h1>
<ul>
<%
    java.util.List emps = (java.util.List) request.getAttribute("employees");
    if (emps != null) {
        for (Object o : emps) {
            testapp.model.Employe e = (testapp.model.Employe) o;
%>
            <li><%= e.getPrenom() %> <%= e.getNom() %></li>
<%
        }
    } else {
%>
        <li>Aucun employe</li>
<%
    }
%>
</ul>
</body>
</html>
