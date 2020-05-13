package yangbot;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.MatchSettings;
import rlbot.manager.BotManager;
import yangbot.cpp.YangBotCppInterop;
import yangbot.path.navmesh.Graph;
import yangbot.path.navmesh.Navigator;
import yangbot.strategy.abstraction.DriveStrikeAbstraction;
import yangbot.util.io.LEDataInputStream;
import yangbot.util.io.PortReader;
import yangbot.util.lut.ArrayLutTable;
import yangbot.util.lut.LutManager;
import yangbot.util.math.vector.Vector3;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class MainClass {

    private static final Integer DEFAULT_PORT = 19265;
    public static BotType BOT_TYPE = BotType.PRODUCTION;

    public static void main(String[] args) {
        System.out.println("I am running Java v" + System.getProperty("java.version"));

        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.FINE);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(Level.FINE);
            h.setFilter(record -> record.getSourceClassName().startsWith("yangbot"));
            h.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format("[%1$tT] [%2$-7s] (%4$s:%5$s): %3$s %n", new Date(lr.getMillis()), lr.getLevel().getName(), lr.getMessage(), lr.getLoggerName(), lr.getSourceMethodName());
                }
            });
        }

        final Logger log = Logger.getLogger("MainClass");

        if (args.length > 0) {
            System.out.println(Arrays.toString(args));
            if (args[0].equalsIgnoreCase("training"))
                BOT_TYPE = BotType.TRAINING;
            else if (args[0].equalsIgnoreCase("test"))
                BOT_TYPE = BotType.TEST;
            else if (args[0].equalsIgnoreCase("trainingtest"))
                BOT_TYPE = BotType.TRAINING_TEST;
        }
        log.info("Using Bot type: " + BOT_TYPE);
        lazyLoadNavigator();
        lazyLoadRLU();
        loadLut();
        BotManager botManager = new BotManager();
        botManager.setRefreshRate(120);
        Integer port = PortReader.readPortFromArgs(args).orElseGet(() -> {
            log.config("Could not read port from args, using default!");
            return DEFAULT_PORT;
        });

        YangPythonInterface pythonInterface = new YangPythonInterface(port, botManager);
        new Thread(pythonInterface::start).start();

        JFrame frame = new JFrame("YangBot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final URL url = MainClass.class.getClassLoader().getResource("icon.png");
        final Image image = Toolkit.getDefaultToolkit().createImage(url);

        final JLabel botsRunning;
        final JPanel outerPanel = new JPanel();

        {
            outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.Y_AXIS));

            var philosophy1 = new JTextArea("I don't know about philosophy, i just use a lot of trial & error and whatever works");
            philosophy1.setEditable(false);
            philosophy1.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            philosophy1.setFont(new Font("Gabriola", Font.PLAIN, 20));
            philosophy1.setBorder(new EmptyBorder(10, 10, 0, 10));
            var philosophy2 = new JTextArea("- Marvin, 12/17/2018");
            philosophy2.setEditable(false);
            philosophy2.setFont(new Font("Bahnschrift", Font.ITALIC, 12));
            philosophy2.setBorder(new EmptyBorder(0, 10, 10, 10));

            outerPanel.add(philosophy1, BorderLayout.CENTER);
            outerPanel.add(philosophy2, BorderLayout.CENTER);

            final JPanel panel = new JPanel();
            {
                panel.setBorder(new EmptyBorder(10, 10, 10, 10));
                final BorderLayout borderLayout = new BorderLayout();
                panel.setLayout(borderLayout);

                JPanel dataPanel = new JPanel();
                {
                    dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
                    dataPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

                    dataPanel.add(new JLabel("Listening on port " + port), BorderLayout.CENTER);

                    botsRunning = new JLabel("Bots running: ");
                    dataPanel.add(botsRunning, BorderLayout.CENTER);
                }
                panel.add(dataPanel, BorderLayout.CENTER);

                panel.add(new JLabel(new ImageIcon(image)), BorderLayout.WEST);
            }
            outerPanel.add(panel, BorderLayout.CENTER);
        }

        frame.add(outerPanel);

        frame.setIconImage(image);

        frame.pack();
        frame.setVisible(true);

        ActionListener botIndexListener = e -> {
            Set<Integer> runningBotIndices = botManager.getRunningBotIndices();

            String botsStr;
            if (runningBotIndices.isEmpty()) {
                botsStr = "None";
            } else {
                botsStr = runningBotIndices.stream()
                        .sorted()
                        .map(i -> "#" + i)
                        .collect(Collectors.joining(", "));
            }
            botsRunning.setText("Bots running: " + botsStr);
        };

        ActionListener mapSettingsListener = f -> {
            try {
                if (botManager.getRunningBotIndices().size() <= 0)
                    return;
                MatchSettings matchSettings = RLBotDll.getMatchSettings();
                YangBotCppInterop.init(matchSettings.gameMode(), matchSettings.gameMap());
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        new Timer(1000, botIndexListener).start();
        new Timer(1000, mapSettingsListener).start();
        lazyPrepJit();
    }

    private static void lazyLoadNavigator() {
        new Thread(MainClass::loadNavigator).start();
    }

    private static void loadNavigator() {
        try {
            var log = Logger.getLogger("NavLoad");

            final ClassLoader cl = ClassLoader.getSystemClassLoader();
            final Thread t = Thread.currentThread();
            t.setPriority(1);
            final int[] parameters = new int[4];
            final List<Graph.Edge> edges = new ArrayList<>();
            final List<Vector3> nav_nodes = new ArrayList<>();
            final List<Vector3> nav_normals = new ArrayList<>();
            final String navmeshPrefix = "soccar";

            long startMs = System.currentTimeMillis();
            log.info("Loading Navigator parameters");
            Thread.sleep(0);
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_parameters.bin"));
                for (int i = 0; i < parameters.length; i++)
                    parameters[i] = para.readIntLE();
            }
            log.fine("Read parameters");
            Thread.sleep(0);
            long ms = System.currentTimeMillis();
            {

                var para = Objects.requireNonNull(cl.getResourceAsStream(navmeshPrefix + "_navigation_edges.bin"));
                byte[] allData = para.readNBytes(para.available());
                assert para.available() <= 0 : "This java version broke or sum";
                assert allData.length % 12 == 0;
                for (int i = 0; i < allData.length; i += 12) { // This is terrible code, but at least it loads the navmesh within 200ms
                    // Basically the code pasted from LEDataInputStream
                    edges.add(new Graph.Edge(((allData[i] & 255) + ((allData[i + 1] & 255) << 8) + ((allData[i + 2] & 255) << 16) + ((allData[i + 3] & 255) << 24)),
                            ((allData[i + 4] & 255) + ((allData[i + 5] & 255) << 8) + ((allData[i + 6] & 255) << 16) + ((allData[i + 7] & 255) << 24)),
                            Float.intBitsToFloat((allData[i + 8] & 255)) + ((allData[i + 9] & 255) << 8) + ((allData[i + 10] & 255) << 16) + ((allData[i + 11] & 255) << 24)));
                }
            }
            log.fine("Read NAV_GRAPH(" + edges.size() + ") " + (System.currentTimeMillis() - ms) + "ms");
            Thread.sleep(0);
            ms = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream(navmeshPrefix + "_navigation_nodes.bin"));
                while (para.available() > 0) {
                    Vector3 vec = new Vector3(para.readFloatLE(), para.readFloatLE(), para.readFloatLE());
                    if (vec.z <= 15)
                        vec = vec.withZ(15);
                    nav_nodes.add(vec);
                }
            }
            log.fine("Read NAV_NODES(" + nav_nodes.size() + ") " + (System.currentTimeMillis() - ms) + "ms");
            Thread.sleep(0);
            ms = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream(navmeshPrefix + "_navigation_normals.bin"));
                while (para.available() > 0) {
                    nav_normals.add(new Vector3(para.readFloatLE(), para.readFloatLE(), para.readFloatLE()));
                }
            }
            log.fine("Read NAV_NORMALS(" + nav_normals.size() + ") " + (System.currentTimeMillis() - ms) + "ms");
            Thread.sleep(0);
            ms = System.currentTimeMillis();

            Navigator.initStatics(parameters, null, null, edges.toArray(new Graph.Edge[0]), nav_nodes.toArray(new Vector3[0]), nav_normals.toArray(new Vector3[0]));

            log.fine("Navigator-Init in " + (System.currentTimeMillis() - ms) + "ms");
            log.info("Done after " + (System.currentTimeMillis() - startMs) + "ms");

            System.gc();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void lazyPrepJit() {
        new Thread(MainClass::prepJit).start();
    }

    private static void prepJit() {
        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            var log = Logger.getLogger("JitPrep");
            log.info("Jitting hot code paths...");

            DriveStrikeAbstraction.prepJit();
            log.info("Jitting done.");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    //private static void lazyLoadLut(){ new Thread(MainClass::loadLut).start();}

    private static void loadLut() {
        try {
            var log = Logger.getLogger("LutLoad");

            final ClassLoader cl = ClassLoader.getSystemClassLoader();
            final Thread t = Thread.currentThread();
            t.setPriority(1);

            long startMs = System.currentTimeMillis();
            log.info("Loading Lut Tables");

            {
                ObjectInputStream para = new ObjectInputStream(cl.getResourceAsStream("lut/powerslide.lut"));
                var lutTable = (ArrayLutTable) para.readObject();
                LutManager.get().registerLut(LutManager.LutIdentifier.DRIFT, lutTable);
            }

            log.info("Done after " + (System.currentTimeMillis() - startMs) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void lazyLoadRLU() {
        new Thread(() -> YangBotCppInterop.init((byte) 0, (byte) 0)).start();
    }

    enum BotType {
        PRODUCTION,
        TEST,
        TRAINING,
        TRAINING_TEST
    }
}
