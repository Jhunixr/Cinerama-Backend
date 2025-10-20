package com.utp.cinerama.cinerama.service.impl;

import com.utp.cinerama.cinerama.dto.SyncResponseDTO;
import com.utp.cinerama.cinerama.dto.TMDbMovieDTO;
import com.utp.cinerama.cinerama.model.Pelicula;
import com.utp.cinerama.cinerama.repository.PeliculaRepository;
import com.utp.cinerama.cinerama.service.PeliculaService;
import com.utp.cinerama.cinerama.service.TMDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeliculaServiceImpl implements PeliculaService {

    private final PeliculaRepository peliculaRepository;
    private final TMDbService tmdbService;

    @Override
    public List<Pelicula> obtenerTodasLasPeliculas() {
        return peliculaRepository.findAll();
    }

    @Override
    public Optional<Pelicula> obtenerPeliculaPorId(Long id) {
        return peliculaRepository.findById(id);
    }

    @Override
    public Pelicula crearPelicula(Pelicula pelicula) {
        return peliculaRepository.save(pelicula);
    }

    @Override
    @Transactional
    public Pelicula actualizarPelicula(Long id, Pelicula pelicula) {
        return peliculaRepository.findById(id)
                .map(p -> {
                    p.setTitulo(pelicula.getTitulo());
                    p.setGenero(pelicula.getGenero());
                    p.setDuracion(pelicula.getDuracion());
                    p.setClasificacion(pelicula.getClasificacion());
                    p.setSinopsis(pelicula.getSinopsis());
                    p.setResumen(pelicula.getResumen());
                    p.setActiva(pelicula.getActiva());
                    return peliculaRepository.save(p);
                })
                .orElseThrow(() -> new RuntimeException("Película no encontrada"));
    }

    @Override
    public void eliminarPelicula(Long id) {
        peliculaRepository.deleteById(id);
    }

    @Override
    public List<Pelicula> buscarPorGenero(String genero) {
        return peliculaRepository.findByGeneroContainingIgnoreCase(genero);
    }

    @Override
    public List<Pelicula> buscarPorTitulo(String titulo) {
        return peliculaRepository.findByTituloContainingIgnoreCase(titulo);
    }

    @Override
    public List<Pelicula> buscarPorClasificacion(String clasificacion) {
        return peliculaRepository.findByClasificacion(clasificacion);
    }

    @Override
    public List<Pelicula> obtenerPeliculasActivas() {
        return peliculaRepository.findByActivaTrue();
    }

    @Override
    public List<Pelicula> obtenerPeliculasPorPopularidad() {
        return peliculaRepository.findByOrderByPopularidadDesc();
    }

    @Override
    public List<Pelicula> obtenerPeliculasPorVoto() {
        return peliculaRepository.findByOrderByVotoPromedioDesc();
    }

    @Override
    public Optional<Pelicula> obtenerPorTmdbId(Long tmdbId) {
        return peliculaRepository.findByTmdbId(tmdbId);
    }

    // ========== METODOS CON PAGINACION ==========

    /**
     * Obtiene peliculas con paginacion
     * 
     * @param page Numero de pagina (empieza en 0)
     * @param size Cantidad de elementos por pagina
     * @param sortBy Campo por el cual ordenar
     * @return Page con las peliculas
     */
    @Override
    public Page<Pelicula> obtenerPeliculasPaginadas(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        return peliculaRepository.findAll(pageable);
    }

    /**
     * Busca peliculas por genero con paginacion
     */
    @Override
    public Page<Pelicula> buscarPorGeneroPaginado(String genero, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return peliculaRepository.findByGeneroContainingIgnoreCase(genero, pageable);
    }

    /**
     * Busca peliculas por titulo con paginacion
     */
    @Override
    public Page<Pelicula> buscarPorTituloPaginado(String titulo, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return peliculaRepository.findByTituloContainingIgnoreCase(titulo, pageable);
    }

    /**
     * Sincroniza películas desde TMDb API
     * @param paginas Número de páginas a sincronizar (1-5 recomendado)
     * @return Resultado de la sincronización
     */
    @Override
    @Transactional
    public SyncResponseDTO sincronizarPeliculasDesdeAPI(Integer paginas) {
        log.info("🚀 Iniciando sincronización de películas desde TMDb (páginas: {})", paginas);
        
        int peliculasNuevas = 0;
        int peliculasActualizadas = 0;
        int peliculasOmitidas = 0;
        int totalPeliculasAPI = 0;

        try {
            // Validar páginas
            if (paginas == null || paginas < 1) {
                paginas = 1;
            }
            if (paginas > 5) {
                paginas = 5; // Limitar a 5 páginas para evitar sobrecarga
            }

            // Obtener películas de cada página
            for (int page = 1; page <= paginas; page++) {
                List<TMDbMovieDTO> moviesFromAPI = tmdbService.getNowPlayingMovies(page);
                totalPeliculasAPI += moviesFromAPI.size();

                for (TMDbMovieDTO tmdbMovie : moviesFromAPI) {
                    try {
                        // Verificar si la película ya existe por tmdbId
                        Optional<Pelicula> existente = peliculaRepository.findByTmdbId(tmdbMovie.getId());

                        if (existente.isPresent()) {
                            // Actualizar película existente
                            Pelicula peliculaExistente = existente.get();
                            actualizarDesdeTMDb(peliculaExistente, tmdbMovie);
                            peliculaRepository.save(peliculaExistente);
                            peliculasActualizadas++;
                            log.debug("✏️ Actualizada: {}", tmdbMovie.getTitle());
                        } else {
                            // Crear nueva película
                            Pelicula nuevaPelicula = convertirDesdeTMDb(tmdbMovie);
                            peliculaRepository.save(nuevaPelicula);
                            peliculasNuevas++;
                            log.debug("➕ Nueva: {}", tmdbMovie.getTitle());
                        }
                    } catch (Exception e) {
                        peliculasOmitidas++;
                        log.warn("⚠️ Error al procesar película {}: {}", tmdbMovie.getTitle(), e.getMessage());
                    }
                }
            }

            String mensaje = String.format(
                "✅ Sincronización completada: %d nuevas, %d actualizadas, %d omitidas de %d totales",
                peliculasNuevas, peliculasActualizadas, peliculasOmitidas, totalPeliculasAPI
            );
            
            log.info(mensaje);

            return SyncResponseDTO.builder()
                    .totalPeliculasAPI(totalPeliculasAPI)
                    .peliculasNuevas(peliculasNuevas)
                    .peliculasActualizadas(peliculasActualizadas)
                    .peliculasOmitidas(peliculasOmitidas)
                    .mensaje(mensaje)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error durante la sincronización: {}", e.getMessage());
            throw new RuntimeException("Error al sincronizar películas: " + e.getMessage(), e);
        }
    }

    /**
     * Convierte un DTO de TMDb a una entidad Pelicula
     */
    private Pelicula convertirDesdeTMDb(TMDbMovieDTO tmdbMovie) {
        return Pelicula.builder()
                .tmdbId(tmdbMovie.getId())
                .titulo(tmdbMovie.getTitle())
                .tituloOriginal(tmdbMovie.getOriginalTitle())
                .idiomaOriginal(tmdbMovie.getOriginalLanguage())
                .genero(tmdbService.mapGenreIdsToNames(tmdbMovie.getGenreIds()))
                .sinopsis(tmdbMovie.getOverview())
                .resumen(tmdbMovie.getOverview())
                .popularidad(tmdbMovie.getPopularity())
                .posterUrl(tmdbMovie.getFullPosterPath())
                .backdropUrl(tmdbMovie.getFullBackdropPath())
                .fechaEstreno(tmdbMovie.getReleaseDateAsLocalDate())
                .votoPromedio(tmdbMovie.getVoteAverage())
                .totalVotos(tmdbMovie.getVoteCount())
                .adult(tmdbMovie.getAdult())
                .activa(true)
                .build();
    }

    /**
     * Actualiza una película existente con datos de TMDb
     */
    private void actualizarDesdeTMDb(Pelicula pelicula, TMDbMovieDTO tmdbMovie) {
        pelicula.setTitulo(tmdbMovie.getTitle());
        pelicula.setTituloOriginal(tmdbMovie.getOriginalTitle());
        pelicula.setIdiomaOriginal(tmdbMovie.getOriginalLanguage());
        pelicula.setGenero(tmdbService.mapGenreIdsToNames(tmdbMovie.getGenreIds()));
        pelicula.setResumen(tmdbMovie.getOverview());
        pelicula.setPopularidad(tmdbMovie.getPopularity());
        pelicula.setPosterUrl(tmdbMovie.getFullPosterPath());
        pelicula.setBackdropUrl(tmdbMovie.getFullBackdropPath());
        pelicula.setFechaEstreno(tmdbMovie.getReleaseDateAsLocalDate());
        pelicula.setVotoPromedio(tmdbMovie.getVoteAverage());
        pelicula.setTotalVotos(tmdbMovie.getVoteCount());
        pelicula.setAdult(tmdbMovie.getAdult());
    }
}