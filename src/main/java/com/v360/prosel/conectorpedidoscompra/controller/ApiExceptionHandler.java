package com.v360.prosel.conectorpedidoscompra.controller;

import com.v360.prosel.conectorpedidoscompra.dto.erro.ErroRespostaDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroRespostaDTO> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String mensagem = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        return ResponseEntity.status(status).body(new ErroRespostaDTO(status, mensagem));
    }
}
