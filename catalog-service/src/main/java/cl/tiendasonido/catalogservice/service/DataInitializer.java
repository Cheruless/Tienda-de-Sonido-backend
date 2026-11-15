package cl.tiendasonido.catalogservice.service;

import cl.tiendasonido.catalogservice.entity.Categoria;
import cl.tiendasonido.catalogservice.entity.Producto;
import cl.tiendasonido.catalogservice.repository.CategoriaRepository;
import cl.tiendasonido.catalogservice.repository.ProductoRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Carga inicial del catálogo: 10 categorías y 51 productos del plan de
 * negocio "SONIDO VIVO" (planilla DSY1104 - Forma B).
 *
 * <p><strong>Patrón:</strong> {@link CommandLineRunner}, igual que
 * {@code auth-service} lo hace con sus usuarios. Spring invoca
 * {@link #run(String...)} una vez, después de que el contexto y la BD
 * están listos.</p>
 *
 * <p><strong>Idempotencia (lo importante):</strong> el seed se puede
 * ejecutar en cada arranque sin romper nada:</p>
 * <ul>
 *   <li>Categorías: solo crea si el nombre (ignorando mayúsculas) no existe;
 *       si ya existe, reutiliza la fila.</li>
 *   <li>Productos: solo crea si el código (ignorando mayúsculas) no existe;
 *       un producto editado por la API se respeta y no se sobreescribe.</li>
 * </ul>
 *
 * <p>Por eso no lanza excepciones ante datos repetidos: detecta antes de
 * insertar en lugar de usar el error de restricción única como control
 * de flujo.</p>
 *
 * @author Exequiel Pulgar Cerda
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public DataInitializer(CategoriaRepository categoriaRepository,
                           ProductoRepository productoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
    }

    /** Las 10 categorías del catálogo, en el orden de la planilla. */
    private static final List<String> CATEGORIAS = List.of(
            "Guitarras Acústicas",
            "Guitarras Eléctricas",
            "Bajos Eléctricos",
            "Baterías",
            "Teclados y Pianos",
            "Amplificadores",
            "Micrófonos",
            "Pedales de Efectos",
            "Accesorios",
            "Estudio y Grabación");

    /**
     * Fila del seed: código, categoría, nombre, marca, modelo, stock,
     * precio CLP y descripción, tal como vienen en la planilla.
     */
    private record Semilla(String codigo, String categoria, String nombre,
                           String marca, String modelo, int stock, int precio,
                           String descripcion) {}

    /** 51 productos (GA/GE/BA/BT/TC/AM/MI/PE/AC/ES). */
    private static final List<Semilla> PRODUCTOS = List.of(
            new Semilla("GA001", "Guitarras Acústicas", "Guitarra Acústica Folk", "Yamaha", "F310", 8, 129990, "Tapa de abeto, aros y fondo de meranti. Ideal para iniciantes."),
            new Semilla("GA002", "Guitarras Acústicas", "Guitarra Acústica Dreadnought", "Fender", "CD-60S", 5, 189990, "Tapa de abeto macizo, brazo de caoba. Sonido cálido y proyectado."),
            new Semilla("GA003", "Guitarras Acústicas", "Guitarra Acústica Clásica 4/4", "Yamaha", "C40", 10, 89990, "Nailon, tapa de abeto. Ideal para estudio y flamenco."),
            new Semilla("GA004", "Guitarras Acústicas", "Guitarra Electroacústica", "Takamine", "GN20CE", 3, 349990, "Pickup integrado, afinador incorporado."),
            new Semilla("GA005", "Guitarras Acústicas", "Guitarra 3/4 Niños", "Yamaha", "JR1", 6, 79990, "Tamaño reducido para niños de 6 a 10 años."),
            new Semilla("GE001", "Guitarras Eléctricas", "Guitarra Eléctrica Stratocaster", "Squier", "Affinity Strat", 5, 249990, "Cuerpo de álamo, mástil de arce, pastillas SSS."),
            new Semilla("GE002", "Guitarras Eléctricas", "Guitarra Eléctrica Les Paul", "Epiphone", "Les Paul Std", 4, 329990, "Cuerpo caoba, tapa arce, pastillas humbucker."),
            new Semilla("GE003", "Guitarras Eléctricas", "Guitarra Eléctrica SG", "Epiphone", "SG Standard", 3, 319990, "Cuerpo caoba, mástil caoba, 2 humbuckers."),
            new Semilla("GE004", "Guitarras Eléctricas", "Guitarra Eléctrica Telecaster", "Squier", "Affinity Tele", 4, 239990, "Cuerpo álamo, clavijero vintage, 2 pastillas single."),
            new Semilla("GE005", "Guitarras Eléctricas", "Guitarra Eléctrica Semi-hollow", "Epiphone", "ES-335", 2, 549990, "Semi-hueca, 2 humbuckers, ideal para jazz y blues."),
            new Semilla("BA001", "Bajos Eléctricos", "Bajo Eléctrico 4 Cuerdas", "Squier", "Affinity PJ", 5, 299990, "Pickup PJ, cuerpo álamo, mástil arce."),
            new Semilla("BA002", "Bajos Eléctricos", "Bajo Eléctrico Jazz Bass", "Fender", "Player Jazz", 2, 699990, "Alder body, 2 Alnico V Jazz single-coil."),
            new Semilla("BA003", "Bajos Eléctricos", "Bajo Acústico 4 Cuerdas", "Yamaha", "APX700II", 2, 429990, "Electroacústico, afinador incorporado."),
            new Semilla("BT001", "Baterías", "Batería Acústica 5 piezas", "Pearl", "Roadshow", 2, 599990, "Incluye stands, platillos y pedal de bombo."),
            new Semilla("BT002", "Baterías", "Batería Electrónica 8 pads", "Roland", "TD-02KV", 2, 799990, "Módulo TD-02, 8 pads de goma, pedal hi-hat."),
            new Semilla("BT003", "Baterías", "Caja Snare 14\"", "Pearl", "STE1450", 4, 89990, "Acero, 14x5\", 10 tensores."),
            new Semilla("BT004", "Baterías", "Platillo Hi-Hat 14\"", "Zildjian", "A Series", 3, 149990, "Latón B20, sonido brillante y claro."),
            new Semilla("BT005", "Baterías", "Platillo Crash 16\"", "Zildjian", "A Series", 3, 129990, "Latón B20, ataque rápido."),
            new Semilla("TC001", "Teclados y Pianos", "Teclado Digital 61 teclas", "Yamaha", "PSR-E373", 4, 249990, "61 teclas sensibles al tacto, 622 voces."),
            new Semilla("TC002", "Teclados y Pianos", "Piano Digital 88 teclas", "Yamaha", "P-45", 2, 499990, "88 teclas pesadas, 10 voces, pedal sustain incluido."),
            new Semilla("TC003", "Teclados y Pianos", "Sintetizador 49 teclas", "Arturia", "MiniLab MKII", 5, 129990, "MIDI controller, 49 mini teclas."),
            new Semilla("TC004", "Teclados y Pianos", "Teclado MIDI 88 teclas", "M-Audio", "Hammer 88", 2, 399990, "88 teclas martillo, sin sonidos propios."),
            new Semilla("AM001", "Amplificadores", "Amplificador Guitarra 15W", "Fender", "Frontman 15G", 5, 99990, "15W, distorsión incorporada, entrada auxiliar."),
            new Semilla("AM002", "Amplificadores", "Amplificador Guitarra 40W", "Marshall", "MG40GFX", 3, 299990, "40W, 4 canales, efectos digitales integrados."),
            new Semilla("AM003", "Amplificadores", "Amplificador Bajo 100W", "Hartke", "HD100", 2, 449990, "100W, tweeter integrado, ecualizador de 4 bandas."),
            new Semilla("AM004", "Amplificadores", "Amplificador Acústico 40W", "Fishman", "Loudbox Mini", 2, 499990, "60W, 2 canales, reverb y chorus incorporados."),
            new Semilla("MI001", "Micrófonos", "Micrófono Dinámico Cardioide", "Shure", "SM58", 8, 149990, "Estándar industria para voz en vivo."),
            new Semilla("MI002", "Micrófonos", "Micrófono Dinámico Instrumento", "Shure", "SM57", 6, 139990, "Ideal para captura de instrumentos y amplificadores."),
            new Semilla("MI003", "Micrófonos", "Micrófono Condensador", "Audio-Tech.", "AT2020", 4, 199990, "Cardioide, XLR, ideal para grabación en estudio."),
            new Semilla("MI004", "Micrófonos", "Micrófono USB de Condensador", "Blue", "Yeti", 5, 299990, "USB, 4 patrones polares, ideal para streaming y podcast."),
            new Semilla("PE001", "Pedales de Efectos", "Pedal Distorsión", "Boss", "DS-1", 7, 79990, "Clásico pedal de distorsión, 3 controles."),
            new Semilla("PE002", "Pedales de Efectos", "Pedal Reverb", "Boss", "RV-6", 4, 179990, "8 modos de reverb, control de shimmer."),
            new Semilla("PE003", "Pedales de Efectos", "Pedal Multi-efectos", "Boss", "ME-80", 2, 349990, "Diseño tipo pedalboard, 8 efectos simultáneos."),
            new Semilla("PE004", "Pedales de Efectos", "Pedal Tuner Cromático", "Boss", "TU-3", 8, 89990, "Afinador cromático, indicador de tono."),
            new Semilla("PE005", "Pedales de Efectos", "Pedal Delay", "MXR", "Carbon Copy", 4, 179990, "Delay analógico cálido, tiempo 600ms."),
            new Semilla("PE006", "Pedales de Efectos", "Pedal Overdrive", "Ibanez", "TS9", 6, 99990, "Tube Screamer clásico, sonido suave y orgánico."),
            new Semilla("AC001", "Accesorios", "Cuerdas Guitarra Eléctrica 09-42", "Ernie Ball", "Super Slinky", 25, 8990, "Juego 6 cuerdas, calibre ligero."),
            new Semilla("AC002", "Accesorios", "Cuerdas Guitarra Acústica 12-53", "Ernie Ball", "Earthwood", 20, 10990, "Bronce fósforo, sonido cálido."),
            new Semilla("AC003", "Accesorios", "Cuerdas Bajo 45-105", "Ernie Ball", "Regular Slinky", 12, 14990, "Cuerdas de níquel enrollado, set 4 cuerdas."),
            new Semilla("AC004", "Accesorios", "Púas de Guitarra x10 (0.73mm)", "Fender", "351", 50, 3990, "Celulosa, grosor medio."),
            new Semilla("AC005", "Accesorios", "Capotraste Guitarra", "Dunlop", "Trigger", 15, 12990, "Capotraste de resorte, compatible 6 cuerdas."),
            new Semilla("AC006", "Accesorios", "Afinador de Clip", "Snark", "SN-5", 20, 8990, "Afinador cromático de clip, pantalla giratoria."),
            new Semilla("AC007", "Accesorios", "Cable Instrumento 3m", "Monster", "S100-I-3", 15, 12990, "Cable trenzado, conectores dorados, 3 metros."),
            new Semilla("AC008", "Accesorios", "Cable Instrumento 6m", "Monster", "S100-I-6", 10, 17990, "Cable trenzado, conectores dorados, 6 metros."),
            new Semilla("AC009", "Accesorios", "Soporte Guitarra de Piso", "Hercules", "GS302B", 12, 22990, "Soporte plegable con enganche automático."),
            new Semilla("AC010", "Accesorios", "Soporte Guitarra de Pared", "Hercules", "WAH-202", 10, 18990, "Montaje a pared, enganche automático."),
            new Semilla("ES001", "Estudio y Grabación", "Interfaz de Audio 2x2 USB", "Focusrite", "Scarlett Solo", 4, 149990, "1 entrada XLR+instrumento, 2 salidas, 24bit/192kHz."),
            new Semilla("ES002", "Estudio y Grabación", "Auriculares de Estudio", "Audio-Tech.", "ATH-M20x", 6, 79990, "Circumaurales, respuesta 15Hz-20kHz."),
            new Semilla("ES003", "Estudio y Grabación", "Auriculares de Estudio Pro", "Audio-Tech.", "ATH-M50x", 4, 219990, "Referencia de industria, sonido neutro y detallado."),
            new Semilla("ES004", "Estudio y Grabación", "Monitor de Estudio 5\"", "Yamaha", "HS5", 2, 349990, "Altavoz activo, respuesta plana, ideal mezcla."),
            new Semilla("ES005", "Estudio y Grabación", "Pop Filter para Micrófono", "Sennheiser", "MZP 40", 8, 14990, "Doble malla, brazo flexible con clip."));

    /**
     * Crea lo que falte y reutiliza lo que ya exista.
     *
     * @param args argumentos de línea de comandos (no se usan)
     */
    @Override
    public void run(String... args) {
        // 1) Categorías: una sola consulta y clave normalizada
        //    (minúsculas) para que "Guitarras Acústicas" y
        //    "guitarras acústicas" cuenten como la misma.
        Map<String, Categoria> categorias = new HashMap<>();
        categoriaRepository.findAll().forEach(c ->
                categorias.put(normalizar(c.getNombre()), c));

        int categoriasCreadas = 0;
        for (String nombre : CATEGORIAS) {
            Categoria existente = categorias.get(normalizar(nombre));
            if (existente != null) {
                continue;
            }
            Categoria categoria = new Categoria();
            categoria.setNombre(nombre);
            categorias.put(normalizar(nombre), categoriaRepository.save(categoria));
            categoriasCreadas++;
        }

        // 2) Productos: solo si el código no existe.
        //    Un producto editado por la API no se sobreescribe.
        int productosCreados = 0;
        for (Semilla s : PRODUCTOS) {
            if (productoRepository.existsByCodigoIgnoreCase(s.codigo())) {
                continue;
            }
            Producto producto = new Producto();
            producto.setCodigo(s.codigo());
            producto.setNombre(s.nombre());
            producto.setMarca(s.marca());
            producto.setModelo(s.modelo());
            producto.setDescripcion(s.descripcion());
            producto.setPrecio(s.precio());
            producto.setStock(s.stock());
            producto.setCategoria(categorias.get(normalizar(s.categoria())));
            productoRepository.save(producto);
            productosCreados++;
        }

        log.info("Seed del catálogo: {} categorías creadas, {} productos creados "
                        + "(total catálogo: {} categorías, {} productos)",
                categoriasCreadas, productosCreados,
                categoriaRepository.count(), productoRepository.count());
    }

    /**
     * Normaliza un nombre para compararlo sin importar mayúsculas ni
     * espacios sobrantes.
     *
     * @param valor nombre a normalizar
     * @return clave de comparación
     */
    private String normalizar(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }
}
