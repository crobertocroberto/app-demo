package com.terrasys.democicd.controller;

import com.terrasys.democicd.model.Employee;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final List<Employee> employees = new ArrayList<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    public EmployeeController() {
        // Datos de ejemplo
        employees.add(new Employee(idCounter.getAndIncrement(), "Juan", "Pérez", "juan.perez@terrasys.com", "Desarrollador"));
        employees.add(new Employee(idCounter.getAndIncrement(), "María", "García", "maria.garcia@terrasys.com", "QA Engineer"));
        employees.add(new Employee(idCounter.getAndIncrement(), "Carlos", "López", "carlos.lopez@terrasys.com", "DevOps Engineer"));
    }

    @GetMapping
    public List<Employee> getAll() {
        return employees;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getById(@PathVariable Long id) {
        return employees.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Employee> create(@RequestBody Employee employee) {
        employee.setId(idCounter.getAndIncrement());
        employees.add(employee);
        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Employee> update(@PathVariable Long id, @RequestBody Employee employee) {
        for (int i = 0; i < employees.size(); i++) {
            if (employees.get(i).getId().equals(id)) {
                employee.setId(id);
                employees.set(i, employee);
                return ResponseEntity.ok(employee);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean removed = employees.removeIf(e -> e.getId().equals(id));
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
