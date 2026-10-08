package com.example.appointmentservice.service.impl;

import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.AppointmentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public AppointmentServiceImpl(AppointmentRepository appointmentRepository, RestTemplate restTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public Appointment createAppointment(Appointment appointment) {
        return AddAppointment(appointment);
    }

    @Override
    @CircuitBreaker(name = "doctorServiceCB")
    public Appointment AddAppointment(Appointment appointment) {
        if (appointment.getPatientId() == null) {
            throw new IllegalArgumentException("Patient ID must not be null");
        }
        if (appointment.getDoctorId() == null) {
            throw new IllegalArgumentException("Doctor ID must not be null");
        }

        // Check Doctor existence / schedule via Doctor-Service on Eureka (Protected by Circuit Breaker)
        String doctorUrl = "http://doctor-service/api/v1/doctors/" + appointment.getDoctorId();
        ResponseEntity<Object> doctorResponse = restTemplate.getForEntity(doctorUrl, Object.class);
        if (!doctorResponse.getStatusCode().is2xxSuccessful() || doctorResponse.getBody() == null) {
            throw new IllegalStateException("Doctor with ID " + appointment.getDoctorId() + " does not exist or Doctor-Service returned empty response.");
        }

        if (appointment.getAppointmentDate() == null) {
            appointment.setAppointmentDate(LocalDateTime.now());
        }

        if (appointment.getStatus() == null || appointment.getStatus().trim().isEmpty()) {
            appointment.setStatus("PENDING");
        }

        return appointmentRepository.save(appointment);
    }

    @Override
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Override
    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id).orElse(null);
    }
}
