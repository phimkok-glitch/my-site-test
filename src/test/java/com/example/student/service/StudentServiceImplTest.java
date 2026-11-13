package com.example.student.service;

import com.example.student.dto.StudentFullResponse;
import com.example.student.dto.StudentRequest;
import com.example.student.dto.StudentResponse;
import com.example.student.dto.StudentUpdateRequest;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.StudentMapper;
import com.example.student.model.Student;
import com.example.student.repository.StudentRepository;
import com.example.student.service.impl.StudentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentMapper studentMapper;

    @InjectMocks
    private StudentServiceImpl studentService;

    @Test
    void saveStudent() {
        StudentRequest request = new StudentRequest();
        Student student = new Student();
        StudentResponse response = new StudentResponse();

        when(studentMapper.requestToStudent(request)).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);
        when(studentMapper.studentToResponse(student)).thenReturn(response);

        assertEquals(response, studentService.save(request));
        verify(studentRepository).save(student);
    }

    @Test
    void findStudentById() {
        Student student = new Student();
        StudentFullResponse response = new StudentFullResponse();

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentMapper.studentToFullResponse(student)).thenReturn(response);

        assertEquals(response, studentService.findById(1L));
    }

    @Test
    void findStudentByIdNotFound() {
        when(studentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> studentService.findById(1L));
    }

    @Test
    void updateStudent() {
        Student student = new Student();
        student.setPhoneNumber("111");
        student.setEmail("old@mail.com");
        StudentUpdateRequest request = new StudentUpdateRequest("222", "new@mail.com");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(student)).thenReturn(student);
        when(studentMapper.studentToResponse(student)).thenReturn(new StudentResponse());

        studentService.update(1L, request);

        assertEquals("222", student.getPhoneNumber());
        assertEquals("new@mail.com", student.getEmail());
    }

    @Test
    void deleteStudent() {
        studentService.deleteById(1L);

        verify(studentRepository).deleteById(1L);
    }
}
