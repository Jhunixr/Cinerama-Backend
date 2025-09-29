package com.utp.cinerama.cinerama.service;

import com.utp.cinerama.cinerama.model.Pago;

import java.util.List;
import java.util.Optional;

public interface PagoService {
    List<Pago> obtenerTodosLosPagos();
    Optional<Pago> obtenerPagoPorId(Long id);
    Pago crearPago(Pago pago);
    void eliminarPago(Long id);
}