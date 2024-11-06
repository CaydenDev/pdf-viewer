package com.pdfviewer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.Tooltip;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.concurrent.Task;
import javafx.application.Platform;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.awt.image.BufferedImage;
import java.io.File;
import javafx.print.PrinterJob;
import javafx.scene.control.ProgressIndicator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import javafx.scene.effect.DropShadow;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import javafx.scene.image.PixelFormat;
import java.nio.IntBuffer;
import java.util.WeakHashMap;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.CheckMenuItem;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.HBox;
import javafx.scene.input.TransferMode;

import java.util.HashMap;
import java.util.Map;
import javafx.geometry.BoundingBox;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.geometry.BoundingBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.geometry.Bounds;

import org.apache.pdfbox.rendering.ImageType;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.ContextMenu;
import javafx.scene.shape.Rectangle;
import javafx.scene.SnapshotParameters;
import javafx.geometry.Rectangle2D;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;

public class PDFViewer extends Application {
    private PDDocument document;
    private PDFRenderer renderer;
    private ImageView imageView;
    private int currentPage = 0;
    private Label pageLabel;
    private double zoomFactor = 1.0;
    private ScrollPane scrollPane;
    private TextField pageInput;
    private ComboBox<String> zoomComboBox;
    private ListView<ImageView> thumbnailList;
    private TextField searchField;
    private VBox sidePanel;
    private ToggleButton thumbnailToggle;
    private ToggleButton searchToggle;
    private double lastX, lastY;
    private boolean isPanning = false;
    private ExecutorService executorService;
    private double lastZoomFactor = 1.0;
    private BufferedImage currentBufferedImage;
    private ConcurrentHashMap<Integer, Image> pageCache;
    private ConcurrentHashMap<Integer, Future<?>> renderingTasks;
    private static final int MAX_CACHE_SIZE = 10;
    private ProgressIndicator loadingIndicator;
    private WeakHashMap<Integer, BufferedImage> bufferedImageCache;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int RENDER_AHEAD_PAGES = 2;
    private volatile boolean isRendering = false;
    private final Object renderLock = new Object();
    private SplitPane splitPane;
    private final Object documentLock = new Object();
    private static final double[] ZOOM_LEVELS = {0.25, 0.33, 0.5, 0.67, 0.75, 0.8, 0.9, 1.0, 1.1, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 4.0};
    private ViewMode currentViewMode = ViewMode.CONTINUOUS;
    private double pageSpacing = 10.0;
    private VBox pagesContainer;
    private Map<Integer, ImageView> pageViews;
    private boolean isLoadingPages = false;

    @Override
    public void start(Stage primaryStage) {
        initializeExecutor();
        initializeComponents();
        
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        VBox topSection = new VBox();
        topSection.getChildren().addAll(
            createMenuBar(primaryStage),
            createMainToolbar(),
            createSecondaryToolbar()
        );
        root.setTop(topSection);

        splitPane = new SplitPane();
        
        sidePanel = createSidePanel();
        sidePanel.setPrefWidth(250);
        
        VBox documentView = new VBox();
        documentView.getChildren().addAll(
            createPageToolbar(),
            createMainContent()
        );
        
        splitPane.getItems().add(documentView);
        root.setCenter(splitPane);

        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/pdf-viewer.css").toExternalForm());
        
        setupKeyboardShortcuts(scene);
        setupMouseHandlers(scene);
        setupDragAndDrop(scene);

        primaryStage.setTitle("PDF Viewer");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    private void initializeComponents() {
        initializeToggleButtons();
        
        pageInput = new TextField("0");
        pageInput.setPrefWidth(50);
        pageInput.setOnAction(e -> goToPage());
        pageInput.setStyle("-fx-background-color: #444444; -fx-text-fill: white; " +
                          "-fx-border-color: #666666; -fx-border-radius: 3;");
        pageInput.setTooltip(new Tooltip("Current Page (Ctrl+G to jump)"));
        
        thumbnailList = new ListView<>();
        thumbnailList.getStyleClass().add("thumbnail-list");
        
        zoomComboBox = new ComboBox<>();
        zoomComboBox.getItems().addAll(
            "25%", "50%", "75%", "100%", "125%", "150%", "200%", "300%", "400%"
        );
        zoomComboBox.setValue("100%");
        zoomComboBox.setEditable(true);
        
        sidePanel = createSidePanel();
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setMaxSize(50, 50);
        
        pageCache = new ConcurrentHashMap<>();
        renderingTasks = new ConcurrentHashMap<>();
        bufferedImageCache = new WeakHashMap<>();
    }

    private MenuBar createMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();
        
        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openItem.setOnAction(e -> openPDF(stage));
        
        MenuItem printItem = new MenuItem("Print");
        printItem.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        printItem.setOnAction(e -> printDocument());
        
        MenuItem closeItem = new MenuItem("Close");
        closeItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        closeItem.setOnAction(e -> closePDF());
        
        fileMenu.getItems().addAll(openItem, new SeparatorMenuItem(), printItem, closeItem);

        Menu viewMenu = new Menu("View");
        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN));
        zoomInItem.setOnAction(e -> zoomIn());
        
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOutItem.setOnAction(e -> zoomOut());
        
        MenuItem fitToWidthItem = new MenuItem("Fit to Width");
        fitToWidthItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        fitToWidthItem.setOnAction(e -> fitToWidth());
        
        MenuItem fitToPageItem = new MenuItem("Fit to Page");
        fitToPageItem.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        fitToPageItem.setOnAction(e -> fitToPage());

        CheckMenuItem showThumbnailsItem = new CheckMenuItem("Show Thumbnails");
        showThumbnailsItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN));
        showThumbnailsItem.selectedProperty().bindBidirectional(thumbnailToggle.selectedProperty());

        viewMenu.getItems().addAll(zoomInItem, zoomOutItem, new SeparatorMenuItem(), 
                                 fitToWidthItem, fitToPageItem, new SeparatorMenuItem(),
                                 showThumbnailsItem);

        Menu navMenu = new Menu("Navigation");
        MenuItem nextPageItem = new MenuItem("Next Page");
        nextPageItem.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT));
        nextPageItem.setOnAction(e -> showNextPage());
        
        MenuItem prevPageItem = new MenuItem("Previous Page");
        prevPageItem.setAccelerator(new KeyCodeCombination(KeyCode.LEFT));
        prevPageItem.setOnAction(e -> showPreviousPage());
        
        MenuItem firstPageItem = new MenuItem("First Page");
        firstPageItem.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
        firstPageItem.setOnAction(e -> goToFirstPage());
        
        MenuItem lastPageItem = new MenuItem("Last Page");
        lastPageItem.setAccelerator(new KeyCodeCombination(KeyCode.END));
        lastPageItem.setOnAction(e -> goToLastPage());
        
        MenuItem goToPageItem = new MenuItem("Go to Page...");
        goToPageItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
        goToPageItem.setOnAction(e -> showGoToPageDialog());

        navMenu.getItems().addAll(prevPageItem, nextPageItem, new SeparatorMenuItem(),
                                 firstPageItem, lastPageItem, new SeparatorMenuItem(),
                                 goToPageItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, navMenu);
        return menuBar;
    }

    private ToolBar createToolBar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("tool-bar");

        Button openButton = createStyledButton("📂 Open", "folder-open");
        openButton.setTooltip(new Tooltip("Open PDF (Ctrl+O)"));
        openButton.setOnAction(e -> openPDF((Stage) toolbar.getScene().getWindow()));

        Button printButton = createStyledButton("🖨️ Print", "print");
        printButton.setTooltip(new Tooltip("Print Document (Ctrl+P)"));
        printButton.setOnAction(e -> printDocument());

        Button firstButton = createStyledButton("⇤", "first");
        firstButton.setTooltip(new Tooltip("First Page (Home)"));
        firstButton.setOnAction(e -> goToFirstPage());
        firstButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> document == null || currentPage == 0,
            pageInput.textProperty()
        ));

        Button prevButton = createStyledButton("←", "previous");
        prevButton.setTooltip(new Tooltip("Previous Page (Left Arrow)"));
        prevButton.setOnAction(event -> {
            showPreviousPage();
            event.consume();
        });

        HBox pageBox = new HBox(5);
        pageBox.setAlignment(Pos.CENTER);
        
        pageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                int page = Integer.parseInt(newVal);
                if (document != null && page >= 1 && page <= document.getNumberOfPages()) {
                    currentPage = page - 1;
                    updatePageDisplay();
                }
            } catch (NumberFormatException e) {
                
            }
        });
        
        Label totalPagesLabel = new Label(" / 0");
        totalPagesLabel.setStyle("-fx-text-fill: white;");
        
        pageBox.getChildren().addAll(pageInput, totalPagesLabel);

        Button nextButton = createStyledButton("→", "next");
        nextButton.setTooltip(new Tooltip("Next Page (Right Arrow)"));
        nextButton.setOnAction(event -> {
            showNextPage();
            event.consume();
        });

        Button lastButton = createStyledButton("⇥", "last");
        lastButton.setTooltip(new Tooltip("Last Page (End)"));
        lastButton.setOnAction(e -> goToLastPage());
        lastButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> document == null || currentPage == document.getNumberOfPages() - 1,
            pageInput.textProperty()
        ));

        thumbnailToggle.setTooltip(new Tooltip("Show/Hide Thumbnails (Ctrl+T)"));
        searchToggle.setTooltip(new Tooltip("Show/Hide Search (Ctrl+F)"));

        toolbar.getItems().addAll(
            new HBox(5, openButton, printButton),
            new Separator(Orientation.VERTICAL),
            new HBox(5, firstButton, prevButton, pageBox, nextButton, lastButton),
            new Separator(Orientation.VERTICAL),
            new HBox(5, thumbnailToggle, searchToggle)
        );

        return toolbar;
    }

    private Button createStyledButton(String text, String id) {
        Button button = new Button(text);
        button.setId(id);
        button.setStyle("-fx-background-color: #444444; -fx-text-fill: white; " +
                       "-fx-background-radius: 3; -fx-border-radius: 3;");
        button.setOnMouseEntered(e -> 
            button.setStyle("-fx-background-color: #555555; -fx-text-fill: white; " +
                          "-fx-background-radius: 3; -fx-border-radius: 3;"));
        button.setOnMouseExited(e -> 
            button.setStyle("-fx-background-color: #444444; -fx-text-fill: white; " +
                          "-fx-background-radius: 3; -fx-border-radius: 3;"));
        return button;
    }

    private HBox createZoomControls() {
        HBox zoomControls = new HBox(5);
        zoomControls.setAlignment(Pos.CENTER_LEFT);
        zoomControls.setPadding(new Insets(5));
        zoomControls.setStyle("-fx-background-color: #333333;");

        Button zoomOutButton = createStyledButton("🔍-", "zoom-out");
        zoomOutButton.setTooltip(new Tooltip("Zoom Out (Ctrl+-)"));
        
        Button zoomInButton = createStyledButton("🔍+", "zoom-in");
        zoomInButton.setTooltip(new Tooltip("Zoom In (Ctrl++)"));
        
        if (zoomComboBox == null) {
            zoomComboBox = new ComboBox<>();
            zoomComboBox.getItems().addAll(
                "25%", "50%", "75%", "100%", "125%", "150%", "200%", "300%", "400%"
            );
            zoomComboBox.setValue("100%");
            zoomComboBox.setEditable(true);
            zoomComboBox.setPrefWidth(100);
        }
        
        zoomOutButton.setOnAction(e -> zoomOut());
        zoomInButton.setOnAction(e -> zoomIn());
        
        zoomComboBox.setOnAction(e -> handleZoomPreset(zoomComboBox.getValue()));
        zoomComboBox.getEditor().setOnAction(e -> handleZoomPreset(zoomComboBox.getEditor().getText()));
        
        Button fitWidthButton = createStyledButton("↔️", "fit-width");
        fitWidthButton.setTooltip(new Tooltip("Fit to Width (Ctrl+Shift+W)"));
        fitWidthButton.setOnAction(e -> fitToWidth());
        
        Button fitPageButton = createStyledButton("⬚", "fit-page");
        fitPageButton.setTooltip(new Tooltip("Fit to Page (Ctrl+Shift+P)"));
        fitPageButton.setOnAction(e -> fitToPage());
        
        zoomControls.getChildren().addAll(
            new HBox(5, zoomOutButton, zoomComboBox, zoomInButton),
            new Separator(Orientation.VERTICAL),
            new HBox(5, fitWidthButton, fitPageButton)
        );

        return zoomControls;
    }

    private void fitToWidth() {
        if (document == null || imageView.getImage() == null) return;
        
        double viewportWidth = scrollPane.getViewportBounds().getWidth() - 40; // Account for padding
        double imageWidth = imageView.getImage().getWidth();
        zoomFactor = viewportWidth / imageWidth;
        zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
        updatePageDisplay();
    }

    private void fitToPage() {
        if (document == null || imageView.getImage() == null) return;
        
        double viewportWidth = scrollPane.getViewportBounds().getWidth() - 40;
        double viewportHeight = scrollPane.getViewportBounds().getHeight() - 40;
        double imageWidth = imageView.getImage().getWidth();
        double imageHeight = imageView.getImage().getHeight();
        
        double widthRatio = viewportWidth / imageWidth;
        double heightRatio = viewportHeight / imageHeight;
        
        zoomFactor = Math.min(widthRatio, heightRatio);
        zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
        updatePageDisplay();
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #333333;");

        pageLabel = new Label("Page: 0/0");
        pageLabel.setStyle("-fx-text-fill: white;");

        Label zoomLabel = new Label();
        zoomLabel.setStyle("-fx-text-fill: white;");
        zoomLabel.textProperty().bind(Bindings.createStringBinding(
            () -> String.format("Zoom: %.0f%%", zoomFactor * 100),
            zoomComboBox.valueProperty()
        ));

        statusBar.getChildren().addAll(pageLabel, new Separator(Orientation.VERTICAL), zoomLabel);
        return statusBar;
    }

    private void openPDF(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            synchronized(documentLock) {
                try {
                    if (document != null) {
                        document.close();
                    }
                    document = PDDocument.load(file);
                    renderer = new PDFRenderer(document);
                    currentPage = 0;
                    
                    pageCache.clear();
                    bufferedImageCache.clear();
                    thumbnailList.getItems().clear();
                    
                    Platform.runLater(() -> {
                        stage.setTitle("PDF Viewer - " + file.getName());
                        pageInput.setText("1");
                        updatePageLabel();
                        updateNavigationButtons();
                    });
                    
                    updatePageDisplay();
                    
                    if (thumbnailToggle.isSelected()) {
                        generateThumbnails();
                    }
                } catch (Exception e) {
                    showError("Error opening PDF: " + e.getMessage());
                }
            }
        }
    }

    private void showPreviousPage() {
        if (document != null && currentPage > 0) {
            synchronized(documentLock) {
                currentPage--;
                Platform.runLater(() -> {
                    pageInput.setText(String.valueOf(currentPage + 1));
                    pageLabel.setText(String.format("Page: %d/%d", currentPage + 1, document.getNumberOfPages()));
                    if (thumbnailList != null && thumbnailList.isVisible()) {
                        thumbnailList.getSelectionModel().select(currentPage);
                        thumbnailList.scrollTo(currentPage);
                    }
                });
                
                renderingTasks.values().forEach(task -> task.cancel(true));
                renderingTasks.clear();
                
                updatePageDisplay();
                updateNavigationButtons();
            }
        }
    }

    private void showNextPage() {
        if (document != null && currentPage < document.getNumberOfPages() - 1) {
            synchronized(documentLock) {
                currentPage++;
                Platform.runLater(() -> {
                    pageInput.setText(String.valueOf(currentPage + 1));
                    pageLabel.setText(String.format("Page: %d/%d", currentPage + 1, document.getNumberOfPages()));
                    if (thumbnailList != null && thumbnailList.isVisible()) {
                        thumbnailList.getSelectionModel().select(currentPage);
                        thumbnailList.scrollTo(currentPage);
                    }
                });
                
                renderingTasks.values().forEach(task -> task.cancel(true));
                renderingTasks.clear();
                
                updatePageDisplay();
                updateNavigationButtons();
            }
        }
    }

    private void updatePageDisplay() {
        if (document == null) return;
        
        synchronized(renderLock) {
            if (isRendering) return;
            isRendering = true;
        }

        showLoadingIndicator(true);

        Task<Image> renderTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                float dpi = 96.0f * (float)zoomFactor;
                float scale = dpi / 72.0f;

                BufferedImage bufferedImage = renderer.renderImageWithDPI(
                    currentPage, 
                    dpi,
                    ImageType.RGB
                );

                BufferedImage correctedImage = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
                );

                float brightness = 0.9f;
                float contrast = 1.1f;
                float gamma = 1.1f;

                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        int rgb = bufferedImage.getRGB(x, y);
                        
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        
                        r = (int)(Math.pow(r / 255.0, gamma) * 255 * brightness);
                        g = (int)(Math.pow(g / 255.0, gamma) * 255 * brightness);
                        b = (int)(Math.pow(b / 255.0, gamma) * 255 * brightness);
                        
                        r = (int)(((r / 255.0 - 0.5) * contrast + 0.5) * 255);
                        g = (int)(((g / 255.0 - 0.5) * contrast + 0.5) * 255);
                        b = (int)(((b / 255.0 - 0.5) * contrast + 0.5) * 255);
                        
                        r = Math.min(255, Math.max(0, r));
                        g = Math.min(255, Math.max(0, g));
                        b = Math.min(255, Math.max(0, b));
                        
                        correctedImage.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
                }

                return createFXImage(correctedImage);
            }
        };

        renderTask.setOnSucceeded(event -> Platform.runLater(() -> {
            Image image = renderTask.getValue();
            imageView.setImage(image);
            
            zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
            
            showLoadingIndicator(false);
            updateUIElements();
            isRendering = false;
            
            prefetchAdjacentPages();
        }));

        renderTask.setOnFailed(event -> Platform.runLater(() -> {
            showLoadingIndicator(false);
            showError("Error rendering page: " + renderTask.getException().getMessage());
            isRendering = false;
        }));

        executorService.submit(renderTask);
    }

    private Image createFXImage(BufferedImage bimg) {
        if (bimg == null) return null;
        
        int width = bimg.getWidth();
        int height = bimg.getHeight();
        
        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter pixelWriter = writableImage.getPixelWriter();
        
        DataBufferInt dataBuffer = (DataBufferInt) bimg.getRaster().getDataBuffer();
        int[] data = dataBuffer.getData();
        
        pixelWriter.setPixels(0, 0, width, height, 
            PixelFormat.getIntArgbPreInstance(),
            data, 0, width);
        
        return writableImage;
    }

    private void prefetchAdjacentPages() {
        if (document == null) return;
        
        List<Integer> pagesToPrefetch = new ArrayList<>();
        
        if (currentPage < document.getNumberOfPages() - 1) {
            pagesToPrefetch.add(currentPage + 1);
        }
        
        if (currentPage > 0) {
            pagesToPrefetch.add(currentPage - 1);
        }
        
        for (int pageNum : pagesToPrefetch) {
            if (!pageCache.containsKey(pageNum)) {
                Task<Void> prefetchTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        float dpi = 96.0f * (float)zoomFactor;
                        float scale = dpi / 72.0f;
                        BufferedImage bufferedImage = renderer.renderImage(pageNum, scale, ImageType.RGB);
                        Image fxImage = createFXImage(bufferedImage);
                        pageCache.put(pageNum, fxImage);
                        bufferedImageCache.put(pageNum, bufferedImage);
                        return null;
                    }
                };
                executorService.submit(prefetchTask);
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.initOwner(scrollPane.getScene().getWindow());
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #333333;");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: white;");
        ((Button) dialogPane.lookupButton(ButtonType.OK)).setStyle(
            "-fx-background-color: #444444; -fx-text-fill: white;"
        );
        
        alert.showAndWait();
    }

    @Override
    public void stop() {
        try {
            synchronized(documentLock) {
                if (document != null) {
                    document.close();
                }
            }
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
            pageCache.clear();
            renderingTasks.clear();
            bufferedImageCache.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (document == null) return;
            
            boolean handled = true;
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case EQUALS: zoomIn(); break;
                    case MINUS: zoomOut(); break;
                    case DIGIT0: resetZoom(); break;
                    case HOME: goToFirstPage(); break;
                    case END: goToLastPage(); break;
                    case G: showGoToPageDialog(); break;
                    case F: searchToggle.setSelected(!searchToggle.isSelected()); break;
                    case T: thumbnailToggle.setSelected(!thumbnailToggle.isSelected()); break;
                    default: handled = false;
                }
            } else {
                switch (event.getCode()) {
                    case LEFT: 
                    case PAGE_UP:
                        showPreviousPage(); 
                        break;
                    case RIGHT:
                    case PAGE_DOWN:
                        showNextPage(); 
                        break;
                    case HOME: 
                        goToFirstPage(); 
                        break;
                    case END: 
                        goToLastPage(); 
                        break;
                    case SPACE:
                        if (event.isShiftDown()) {
                            showPreviousPage();
                        } else {
                            showNextPage();
                        }
                        break;
                    default: 
                        handled = false;
                }
            }
            
            if (handled) {
                event.consume();
            }
        });
    }

    private void zoomIn() {
        if (document == null) return;
        
        double newZoom = zoomFactor * 1.25;
        if (newZoom <= 5.0) {
            zoomFactor = newZoom;
            zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
            updatePageDisplay();
        }
    }

    private void zoomOut() {
        if (document == null) return;
        
        double newZoom = zoomFactor * 0.8;
        if (newZoom >= 0.1) {
            zoomFactor = newZoom;
            zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
            updatePageDisplay();
        }
    }

    private void resetZoom() {
        zoomFactor = 1.0;
        updatePageDisplay();
    }

    private void goToPage() {
        try {
            int page = Integer.parseInt(pageInput.getText());
            if (document != null && page >= 1 && page <= document.getNumberOfPages()) {
                currentPage = page - 1;
                updatePageDisplay();
            }
        } catch (NumberFormatException e) {
            showError("Invalid page number");
        }
    }

    private void goToFirstPage() {
        if (document != null) {
            currentPage = 0;
            updatePageDisplay();
            Platform.runLater(() -> {
                pageInput.setText("1");
                if (thumbnailList != null && thumbnailList.isVisible()) {
                    thumbnailList.getSelectionModel().select(0);
                    thumbnailList.scrollTo(0);
                }
            });
        }
    }

    private void goToLastPage() {
        if (document != null) {
            currentPage = document.getNumberOfPages() - 1;
            updatePageDisplay();
            Platform.runLater(() -> {
                pageInput.setText(String.valueOf(document.getNumberOfPages()));
                if (thumbnailList != null && thumbnailList.isVisible()) {
                    thumbnailList.getSelectionModel().select(currentPage);
                    thumbnailList.scrollTo(currentPage);
                }
            });
        }
    }

    private VBox createSidePanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(250);
        panel.setStyle("-fx-background-color: #333333;");
        panel.setPadding(new Insets(5));

        TabPane sidebarTabs = new TabPane();
        sidebarTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        Tab thumbnailsTab = new Tab("Thumbnails");
        thumbnailsTab.setGraphic(new Label("📑"));
        VBox thumbnailsBox = new VBox(5);
        thumbnailsBox.getChildren().addAll(
            new Label("Page Thumbnails"),
            thumbnailList
        );
        thumbnailsTab.setContent(thumbnailsBox);
        
        Tab outlineTab = new Tab("Outline");
        outlineTab.setGraphic(new Label("📚"));
        TreeView<String> outlineView = new TreeView<>();
        outlineTab.setContent(outlineView);
        
        Tab searchTab = new Tab("Search");
        searchTab.setGraphic(new Label("🔍"));
        VBox searchBox = createSearchPanel();
        searchTab.setContent(searchBox);
        
        sidebarTabs.getTabs().addAll(thumbnailsTab, outlineTab, searchTab);
        
        panel.getChildren().add(sidebarTabs);
        VBox.setVgrow(sidebarTabs, Priority.ALWAYS);

        return panel;
    }

    private VBox createSearchPanel() {
        VBox searchPanel = new VBox(10);
        searchPanel.setPadding(new Insets(10));
        
        Label searchLabel = new Label("Search Document");
        searchField = new TextField();
        searchField.setPromptText("Enter search text...");
        
        HBox searchOptions = new HBox(10);
        CheckBox matchCase = new CheckBox("Match case");
        CheckBox wholeWords = new CheckBox("Whole words");
        searchOptions.getChildren().addAll(matchCase, wholeWords);
        
        Button searchPrevious = new Button("Previous");
        Button searchNext = new Button("Next");
        HBox searchButtons = new HBox(5, searchPrevious, searchNext);
        
        Label searchResults = new Label("No results");
        searchResults.setStyle("-fx-text-fill: #888888;");
        
        searchPanel.getChildren().addAll(
            searchLabel,
            searchField,
            searchOptions,
            searchButtons,
            searchResults
        );
        
        return searchPanel;
    }

    private void setupMouseHandlers(Scene scene) {
        scene.setOnScroll(e -> {
            if (e.isControlDown()) {
                if (e.getDeltaY() > 0) {
                    zoomIn();
                } else {
                    zoomOut();
                }
                e.consume();
            }
        });

        scene.setOnMousePressed(e -> {
            if (e.isMiddleButtonDown() || (e.isPrimaryButtonDown() && e.isControlDown())) {
                isPanning = true;
                lastX = e.getSceneX();
                lastY = e.getSceneY();
                scene.setCursor(Cursor.MOVE);
            }
        });

        scene.setOnMouseDragged(e -> {
            if (isPanning) {
                scrollPane.setHvalue(scrollPane.getHvalue() - (e.getSceneX() - lastX) / scrollPane.getWidth());
                scrollPane.setVvalue(scrollPane.getVvalue() - (e.getSceneY() - lastY) / scrollPane.getHeight());
                lastX = e.getSceneX();
                lastY = e.getSceneY();
            }
        });

        scene.setOnMouseReleased(e -> {
            if (isPanning) {
                isPanning = false;
                scene.setCursor(Cursor.DEFAULT);
            }
        });

        imageView.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem downloadPage = new MenuItem("Download Current Page");
            downloadPage.setGraphic(new Label("💾"));
            downloadPage.setOnAction(event -> downloadCurrentPage());
            
            MenuItem startSnipping = new MenuItem("Start Snipping Tool");
            startSnipping.setGraphic(new Label("✂️"));
            startSnipping.setOnAction(event -> startSnippingTool());
            
            contextMenu.getItems().addAll(downloadPage, startSnipping);
            contextMenu.show(imageView, e.getScreenX(), e.getScreenY());
        });
    }

    private void downloadCurrentPage() {
        if (document == null) return;
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Page as PDF");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg")
        );
        
        File file = fileChooser.showSaveDialog(scrollPane.getScene().getWindow());
        if (file != null) {
            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    if (file.getName().toLowerCase().endsWith(".pdf")) {
                        PDDocument singlePageDoc = new PDDocument();
                        singlePageDoc.addPage(document.getPage(currentPage));
                        singlePageDoc.save(file);
                        singlePageDoc.close();
                    } else {
                        BufferedImage pageImage = renderer.renderImage(currentPage, 300);
                        String format = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                        ImageIO.write(pageImage, format, file);
                    }
                    return null;
                }
            };
            
            saveTask.setOnFailed(e -> showError("Error saving page: " + saveTask.getException().getMessage()));
            executorService.submit(saveTask);
        }
    }

    private Rectangle snippingRect;
    private double startX, startY;

    private void startSnippingTool() {
        Stage snippingStage = new Stage(StageStyle.TRANSPARENT);
        snippingStage.initModality(Modality.APPLICATION_MODAL);
        
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
        
        snippingRect = new Rectangle();
        snippingRect.setFill(null);
        snippingRect.setStroke(Color.RED);
        snippingRect.setStrokeWidth(2);
        snippingRect.getStrokeDashArray().addAll(6d, 6d);
        
        root.getChildren().add(snippingRect);
        Scene scene = new Scene(root, imageView.getFitWidth(), imageView.getFitHeight());
        scene.setFill(null);
        
        scene.setOnMousePressed(e -> {
            startX = e.getX();
            startY = e.getY();
            snippingRect.setX(startX);
            snippingRect.setY(startY);
            snippingRect.setWidth(0);
            snippingRect.setHeight(0);
        });
        
        scene.setOnMouseDragged(e -> {
            snippingRect.setWidth(Math.abs(e.getX() - startX));
            snippingRect.setHeight(Math.abs(e.getY() - startY));
            snippingRect.setX(Math.min(e.getX(), startX));
            snippingRect.setY(Math.min(e.getY(), startY));
        });
        
        scene.setOnMouseReleased(e -> {
            if (snippingRect.getWidth() > 10 && snippingRect.getHeight() > 10) {
                saveSnippedArea();
            }
            snippingStage.close();
        });
        
        snippingStage.setScene(scene);
        snippingStage.show();
    }

    private void saveSnippedArea() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Snippet");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg")
        );
        
        File file = fileChooser.showSaveDialog(scrollPane.getScene().getWindow());
        if (file != null) {
            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    WritableImage snapshot = new WritableImage(
                        (int) snippingRect.getWidth(),
                        (int) snippingRect.getHeight()
                    );
                    
                    SnapshotParameters params = new SnapshotParameters();
                    params.setViewport(new Rectangle2D(
                        snippingRect.getX(),
                        snippingRect.getY(),
                        snippingRect.getWidth(),
                        snippingRect.getHeight()
                    ));
                    
                    imageView.snapshot(params, snapshot);
                    
                    BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
                    String format = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                    ImageIO.write(bufferedImage, format, file);
                    
                    return null;
                }
            };
            
            saveTask.setOnFailed(e -> showError("Error saving snippet: " + saveTask.getException().getMessage()));
            executorService.submit(saveTask);
        }
    }

    private void toggleThumbnails() {
        if (thumbnailToggle.isSelected()) {
            if (!splitPane.getItems().contains(sidePanel)) {
                splitPane.getItems().add(0, sidePanel);
                splitPane.setDividerPositions(0.2);
            }
            
            thumbnailList.setVisible(true);
            thumbnailList.setManaged(true);
            VBox searchPanel = (VBox) searchField.getParent();
            searchPanel.setVisible(false);
            searchPanel.setManaged(false);
            
            if (document != null && thumbnailList.getItems().isEmpty()) {
                generateThumbnails();
            }
            
            searchToggle.setSelected(false);
        } else {
            if (!searchToggle.isSelected()) {
                splitPane.getItems().remove(sidePanel);
            }
            thumbnailList.setVisible(false);
            thumbnailList.setManaged(false);
        }
    }

    private void toggleSearch() {
        VBox searchPanel = (VBox) searchField.getParent();
        if (searchToggle.isSelected()) {
            if (!splitPane.getItems().contains(sidePanel)) {
                splitPane.getItems().add(0, sidePanel);
                splitPane.setDividerPositions(0.2);
            }
            searchPanel.setVisible(true);
            searchPanel.setManaged(true);
            thumbnailList.setVisible(false);
            thumbnailList.setManaged(false);
            thumbnailToggle.setSelected(false);
        } else {
            searchPanel.setVisible(false);
            searchPanel.setManaged(false);
            if (!thumbnailToggle.isSelected()) {
                splitPane.getItems().remove(sidePanel);
            }
        }
    }

    private void updateThumbnails() {
        if (document == null || !thumbnailList.isVisible()) return;

        Task<Void> thumbnailTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                thumbnailList.getItems().clear();
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    if (isCancelled()) break;
                    
                    final int pageNum = i;
                    BufferedImage thumb = renderer.renderImage(i, 0.2f);
                    ImageView thumbView = new ImageView(createFXImage(thumb));
                    thumbView.setFitWidth(150);
                    thumbView.setPreserveRatio(true);
                    
                    
                    thumbView.setOnMouseClicked(e -> {
                        currentPage = pageNum;
                        updatePageDisplay();
                    });
                    
                    Platform.runLater(() -> thumbnailList.getItems().add(thumbView));
                }
                return null;
            }
        };

        executorService.submit(thumbnailTask);
    }

    private ScrollPane createMainContent() {
        scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: #404040; -fx-background-color: #404040;");
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        
        imageView.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem downloadPage = new MenuItem("Download Current Page");
            downloadPage.setGraphic(new Label("💾"));
            downloadPage.setOnAction(event -> downloadCurrentPage());
            
            MenuItem startSnipping = new MenuItem("Start Snipping Tool");
            startSnipping.setGraphic(new Label("✂️"));
            startSnipping.setOnAction(event -> startSnippingTool());
            
            contextMenu.getItems().addAll(downloadPage, startSnipping);
            contextMenu.show(imageView, e.getScreenX(), e.getScreenY());
        });
        
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setStyle("-fx-background-color: #404040;");
        imageContainer.setPadding(new Insets(20));
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setMaxSize(50, 50);
        imageContainer.getChildren().add(loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);
        
        scrollPane.setContent(imageContainer);
        
        return scrollPane;
    }

    private void printDocument() {
        if (document == null) {
            showError("No document loaded");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showError("No printer found");
            return;
        }

        if (job.showPrintDialog(scrollPane.getScene().getWindow())) {
            Stage progressStage = new Stage();
            progressStage.initModality(Modality.APPLICATION_MODAL);
            progressStage.initStyle(StageStyle.UNDECORATED);
            
            ProgressBar progress = new ProgressBar();
            progress.setPrefWidth(300);
            Label progressLabel = new Label("Printing...");
            progressLabel.setStyle("-fx-text-fill: white;");
            
            VBox progressBox = new VBox(10);
            progressBox.setAlignment(Pos.CENTER);
            progressBox.setPadding(new Insets(20));
            progressBox.setStyle("-fx-background-color: #333333;");
            progressBox.getChildren().addAll(progressLabel, progress);
            
            Scene progressScene = new Scene(progressBox);
            progressStage.setScene(progressScene);
            progressStage.show();

            Task<Void> printTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    int totalPages = document.getNumberOfPages();
                    for (int i = 0; i < totalPages; i++) {
                        if (isCancelled()) break;
                        
                        updateProgress(i, totalPages);
                        updateMessage(String.format("Printing page %d of %d", i + 1, totalPages));
                        
                        final int pageNum = i;
                        BufferedImage image = renderer.renderImage(pageNum, 300f / 72f);
                        Image fxImage = createFXImage(image);
                        image.flush();
                        
                        Platform.runLater(() -> {
                            ImageView printView = new ImageView(fxImage);
                            printView.setFitWidth(job.getJobSettings().getPageLayout().getPrintableWidth());
                            printView.setPreserveRatio(true);
                            
                            if (!job.printPage(printView)) {
                                job.cancelJob();
                                showError("Printing failed for page " + (pageNum + 1));
                            }
                        });
                        
                        Thread.sleep(100);
                    }
                    return null;
                }
            };

            progress.progressProperty().bind(printTask.progressProperty());
            progressLabel.textProperty().bind(printTask.messageProperty());
            
            printTask.setOnSucceeded(e -> {
                job.endJob();
                progressStage.close();
            });
            
            printTask.setOnFailed(e -> {
                job.cancelJob();
                progressStage.close();
                showError("Printing failed: " + printTask.getException().getMessage());
            });

            executorService.submit(printTask);
        }
    }

    private void initializeToggleButtons() {
        thumbnailToggle = new ToggleButton("Thumbnails");
        thumbnailToggle.setTooltip(new Tooltip("Show/Hide Thumbnails"));
        thumbnailToggle.setOnAction(e -> toggleThumbnails());
        thumbnailToggle.getStyleClass().add("toggle-button");

        searchToggle = new ToggleButton("Search");
        searchToggle.setTooltip(new Tooltip("Show/Hide Search"));
        searchToggle.setOnAction(e -> toggleSearch());
        searchToggle.getStyleClass().add("toggle-button");
        
        String buttonStyle = "-fx-background-color: #444444; -fx-text-fill: white; " +
                            "-fx-background-radius: 3; -fx-border-radius: 3;";
        thumbnailToggle.setStyle(buttonStyle);
        searchToggle.setStyle(buttonStyle);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void maintainCache() {
        if (pageCache.size() > MAX_CACHE_SIZE) {
            pageCache.keySet().stream()
                .sorted((a, b) -> Integer.compare(
                    Math.abs(b - currentPage),
                    Math.abs(a - currentPage)))
                .skip(MAX_CACHE_SIZE)
                .forEach(key -> {
                    pageCache.remove(key);
                    bufferedImageCache.remove(key);
                });
        }
    }

    private void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show && !loadingIndicator.isVisible()) {
                FadeTransition ft = new FadeTransition(Duration.millis(200), loadingIndicator);
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
                loadingIndicator.setVisible(true);
            } else if (!show && loadingIndicator.isVisible()) {
                FadeTransition ft = new FadeTransition(Duration.millis(200), loadingIndicator);
                ft.setFromValue(1);
                ft.setToValue(0);
                ft.setOnFinished(e -> loadingIndicator.setVisible(false));
                ft.play();
            }
        });
    }

    private void updateUIElements() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateUIElements);
            return;
        }
        
        if (document != null) {
            pageLabel.setText(String.format("Page: %d/%d", currentPage + 1, document.getNumberOfPages()));
            pageInput.setText(String.valueOf(currentPage + 1));
            
            zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
            
            if (thumbnailList != null && thumbnailList.isVisible() && 
                currentPage < thumbnailList.getItems().size()) {
                thumbnailList.getSelectionModel().select(currentPage);
                thumbnailList.scrollTo(currentPage);
            }
        }
    }

    private void prefetchPages() {
        if (document == null) return;
        
        for (int i = 1; i <= RENDER_AHEAD_PAGES; i++) {
            int pageNum = currentPage + i;
            if (pageNum >= document.getNumberOfPages()) break;
            
            if (!pageCache.containsKey(pageNum)) {
                prefetchPage(pageNum);
            }
        }
        
        for (int i = 1; i <= RENDER_AHEAD_PAGES; i++) {
            int pageNum = currentPage - i;
            if (pageNum < 0) break;
            
            if (!pageCache.containsKey(pageNum)) {
                prefetchPage(pageNum);
            }
        }
    }

    private void prefetchPage(int pageNum) {
        Task<Void> prefetchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!pageCache.containsKey(pageNum)) {
                    float scale = 2.0f * (float)zoomFactor;
                    BufferedImage bufferedImage = renderer.renderImage(pageNum, scale);
                    Image fxImage = createFXImage(bufferedImage);
                    pageCache.put(pageNum, fxImage);
                    bufferedImageCache.put(pageNum, bufferedImage);
                }
                return null;
            }
        };
        
        executorService.submit(prefetchTask);
    }

    @Override
    public void init() {
        pageCache = new ConcurrentHashMap<>();
        renderingTasks = new ConcurrentHashMap<>();
        bufferedImageCache = new WeakHashMap<>();
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setMaxSize(50, 50);
    }

    private void generateThumbnails() {
        if (document == null) return;
        
        Platform.runLater(() -> thumbnailList.getItems().clear());
        
        Task<Void> thumbnailTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                synchronized(documentLock) {
                    int totalPages = document.getNumberOfPages();
                    
                    for (int i = 0; i < totalPages && !isCancelled(); i++) {
                        final int pageNum = i;
                        try {
                            updateProgress(i, totalPages);
                            
                            BufferedImage thumb = renderer.renderImage(pageNum, 0.15f);
                            Image fxImage = createFXImage(thumb);
                            thumb.flush();
                            
                            ImageView thumbView = new ImageView(fxImage);
                            thumbView.setFitWidth(150);
                            thumbView.setPreserveRatio(true);
                            thumbView.setSmooth(true);
                            
                            final int finalPageNum = pageNum;
                            thumbView.setOnMouseClicked(e -> Platform.runLater(() -> {
                                if (finalPageNum != currentPage) {
                                    currentPage = finalPageNum;
                                    updatePageDisplay();
                                }
                            }));
                            
                            Platform.runLater(() -> {
                                thumbnailList.getItems().add(thumbView);
                                if (pageNum == currentPage) {
                                    thumbnailList.getSelectionModel().select(pageNum);
                                    thumbnailList.scrollTo(pageNum);
                                }
                            });
                            
                            Thread.sleep(20);
                            
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }
        };

        ProgressIndicator progress = new ProgressIndicator();
        progress.progressProperty().bind(thumbnailTask.progressProperty());
        progress.setMaxSize(50, 50);
        
        VBox progressBox = new VBox(10);
        progressBox.setAlignment(Pos.CENTER);
        Label progressLabel = new Label("Generating thumbnails...");
        progressLabel.setStyle("-fx-text-fill: white;");
        progressBox.getChildren().addAll(progress, progressLabel);
        
        Platform.runLater(() -> thumbnailList.setPlaceholder(progressBox));
        
        thumbnailTask.setOnSucceeded(e -> Platform.runLater(() -> {
            thumbnailList.setPlaceholder(new Label("No pages to display"));
            if (currentPage < thumbnailList.getItems().size()) {
                thumbnailList.getSelectionModel().select(currentPage);
                thumbnailList.scrollTo(currentPage);
            }
        }));
        
        thumbnailTask.setOnFailed(e -> Platform.runLater(() -> {
            showError("Error generating thumbnails: " + thumbnailTask.getException().getMessage());
            thumbnailList.setPlaceholder(new Label("Error generating thumbnails"));
        }));
        
        executorService.submit(thumbnailTask);
    }

    private void closePDF() {
        synchronized(documentLock) {
            if (document != null) {
                try {
                    document.close();
                    document = null;
                    renderer = null;
                    currentPage = 0;
                    imageView.setImage(null);
                    thumbnailList.getItems().clear();
                    pageCache.clear();
                    bufferedImageCache.clear();
                    updateUIElements();
                } catch (Exception e) {
                    showError("Error closing PDF: " + e.getMessage());
                }
            }
        }
    }

    private void showGoToPageDialog() {
        if (document == null) return;

        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentPage + 1));
        dialog.setTitle("Go to Page");
        dialog.setHeaderText("Enter page number (1-" + document.getNumberOfPages() + "):");
        dialog.setContentText("Page:");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #333333;");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: white;");
        TextField input = dialog.getEditor();
        input.setStyle("-fx-background-color: #444444; -fx-text-fill: white;");
        
        dialogPane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialogPane.lookupButton(ButtonType.OK).setStyle("-fx-background-color: #444444; -fx-text-fill: white;");
        dialogPane.lookupButton(ButtonType.CANCEL).setStyle("-fx-background-color: #444444; -fx-text-fill: white;");

        dialog.showAndWait().ifPresent(result -> {
            try {
                int page = Integer.parseInt(result);
                if (page >= 1 && page <= document.getNumberOfPages()) {
                    currentPage = page - 1;
                    updatePageDisplay();
                } else {
                    showError("Invalid page number");
                }
            } catch (NumberFormatException e) {
                showError("Invalid page number");
            }
        });
    }

    private static class ToolbarGroup extends HBox {
        public ToolbarGroup(Node... nodes) {
            super(5);
            setAlignment(Pos.CENTER);
            getStyleClass().add("toolbar-group");
            getChildren().addAll(nodes);
        }
    }

    private void updatePageLabel() {
        if (document != null) {
            pageLabel.setText(String.format("Page: %d/%d", currentPage + 1, document.getNumberOfPages()));
            
            ToolBar toolbar = (ToolBar) pageInput.getParent().getParent();
            for (Node node : toolbar.getItems()) {
                if (node instanceof HBox) {
                    for (Node child : ((HBox) node).getChildren()) {
                        if (child instanceof Label && ((Label) child).getText().startsWith(" / ")) {
                            ((Label) child).setText(String.format(" / %d", document.getNumberOfPages()));
                            break;
                        }
                    }
                }
            }
        }
    }

    private void updateNavigationButtons() {
        Platform.runLater(() -> {
            boolean hasDocument = document != null;
            boolean isFirstPage = currentPage == 0;
            boolean isLastPage = hasDocument && currentPage == document.getNumberOfPages() - 1;
            
            ToolBar toolbar = (ToolBar) pageInput.getParent().getParent();
            for (Node node : toolbar.getItems()) {
                if (node instanceof HBox) {
                    for (Node child : ((HBox) node).getChildren()) {
                        if (child instanceof Button) {
                            Button button = (Button) child;
                            switch (button.getId()) {
                                case "first":
                                case "previous":
                                    button.setDisable(!hasDocument || isFirstPage);
                                    break;
                                case "next":
                                case "last":
                                    button.setDisable(!hasDocument || isLastPage);
                                    break;
                            }
                        }
                    }
                }
            }
        });
    }

    private void rotatePage() {
        showError("Page rotation not yet implemented");
    }

    private void initializeExecutor() {
        executorService = new ThreadPoolExecutor(
            THREAD_POOL_SIZE,
            THREAD_POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        );
    }

    private ToolBar createMainToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("main-toolbar");

        Button openButton = createToolbarButton("Open File", "folder-open", e -> openPDF((Stage) toolbar.getScene().getWindow()));
        openButton.setGraphic(new Label("📂"));
        openButton.setContentDisplay(ContentDisplay.LEFT);

        Button printButton = createToolbarButton("Print", "print", e -> printDocument());
        printButton.setGraphic(new Label("🖨️"));
        printButton.setContentDisplay(ContentDisplay.LEFT);

        Button firstButton = createToolbarButton("First", "first", e -> goToFirstPage());
        firstButton.setTooltip(new Tooltip("Go to first page (Home)"));

        Button prevButton = createToolbarButton("Previous", "previous", e -> showPreviousPage());
        prevButton.setTooltip(new Tooltip("Previous page (Left Arrow)"));

        HBox pageBox = new HBox(5);
        pageBox.setAlignment(Pos.CENTER);
        pageBox.getChildren().addAll(
            new Label("Page"),
            pageInput,
            new Label("of"),
            new Label() {{
                textProperty().bind(Bindings.createStringBinding(
                    () -> document != null ? String.valueOf(document.getNumberOfPages()) : "0",
                    pageInput.textProperty()
                ));
            }}
        );
        pageBox.getStyleClass().add("page-box");

        Button nextButton = createToolbarButton("Next", "next", e -> showNextPage());
        nextButton.setTooltip(new Tooltip("Next page (Right Arrow)"));

        Button lastButton = createToolbarButton("Last", "last", e -> goToLastPage());
        lastButton.setTooltip(new Tooltip("Go to last page (End)"));

        ToggleButton sidebarButton = new ToggleButton("Thumbnails");
        sidebarButton.setTooltip(new Tooltip("Show/Hide Thumbnails Panel (F4)"));
        sidebarButton.selectedProperty().bindBidirectional(thumbnailToggle.selectedProperty());

        Button findButton = createToolbarButton("Find", "search", e -> toggleSearch());
        findButton.setTooltip(new Tooltip("Search in document (Ctrl+F)"));

        Label zoomLabel = new Label("Zoom:");
        ComboBox<String> zoomPresets = new ComboBox<>();
        zoomPresets.getItems().addAll(
            "Auto",
            "Page Width",
            "Page Fit",
            "50%",
            "75%",
            "100%",
            "125%",
            "150%",
            "200%"
        );
        zoomPresets.setValue("100%");
        zoomPresets.setEditable(true);
        zoomPresets.setPrefWidth(100);
        zoomPresets.setOnAction(e -> handleZoomPreset(zoomPresets.getValue()));

        Button zoomOutButton = createToolbarButton("Zoom Out", "zoom-out", e -> zoomOut());
        Button zoomInButton = createToolbarButton("Zoom In", "zoom-in", e -> zoomIn());

        ToggleGroup toolsGroup = new ToggleGroup();
        
        ToggleButton selectButton = new ToggleButton("Select");
        selectButton.setToggleGroup(toolsGroup);
        selectButton.setTooltip(new Tooltip("Select text (S)"));

        ToggleButton handButton = new ToggleButton("Hand");
        handButton.setToggleGroup(toolsGroup);
        handButton.setTooltip(new Tooltip("Pan tool (H)"));
        handButton.setSelected(true);

        toolbar.getItems().addAll(
            new ToolbarGroup(openButton, printButton),
            new Separator(),
            new ToolbarGroup(firstButton, prevButton, pageBox, nextButton, lastButton),
            new Separator(),
            new ToolbarGroup(zoomLabel, zoomOutButton, zoomPresets, zoomInButton),
            new Separator(),
            new ToolbarGroup(sidebarButton, findButton),
            new Separator(),
            new ToolbarGroup(selectButton, handButton)
        );

        return toolbar;
    }

    private ToolBar createSecondaryToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("secondary-toolbar");

        ToggleButton sidebarButton = new ToggleButton("☰");
        sidebarButton.setTooltip(new Tooltip("Show Sidebar (Ctrl+B)"));
        sidebarButton.selectedProperty().bindBidirectional(thumbnailToggle.selectedProperty());

        Button handToolButton = createStyledButton("✋", "hand-tool");
        handToolButton.setTooltip(new Tooltip("Hand Tool (H)"));

        Button selectToolButton = createStyledButton("◫", "select-tool");
        selectToolButton.setTooltip(new Tooltip("Select Tool (S)"));

        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setPrefWidth(200);
        searchField.getStyleClass().add("search-field");

        toolbar.getItems().addAll(
            sidebarButton,
            new Separator(),
            new HBox(5, handToolButton, selectToolButton),
            new Separator(),
            searchField
        );

        return toolbar;
    }

    private ToolBar createPageToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("page-toolbar");

        Button fitWidthButton = createStyledButton("↔️", "fit-width");
        fitWidthButton.setTooltip(new Tooltip("Fit to Width"));
        fitWidthButton.setOnAction(e -> fitToWidth());

        Button fitPageButton = createStyledButton("⬚", "fit-page");
        fitPageButton.setTooltip(new Tooltip("Fit to Page"));
        fitPageButton.setOnAction(e -> fitToPage());

        Button rotateButton = createStyledButton("⟳", "rotate");
        rotateButton.setTooltip(new Tooltip("Rotate"));
        rotateButton.setOnAction(e -> rotatePage());

        toolbar.getItems().addAll(
            new HBox(5, fitWidthButton, fitPageButton, rotateButton)
        );

        return toolbar;
    }

    private void setupDragAndDrop(Scene scene) {
        scene.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        scene.setOnDragDropped(event -> {
            boolean success = false;
            if (event.getDragboard().hasFiles()) {
                File file = event.getDragboard().getFiles().get(0);
                if (file.getName().toLowerCase().endsWith(".pdf")) {
                    openPDF((Stage) scene.getWindow());
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private Button createToolbarButton(String text, String tooltip, EventHandler<ActionEvent> action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(action);
        button.getStyleClass().add("toolbar-button");
        return button;
    }

    private ToggleButton createToolbarToggleButton(String text, String tooltip, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setToggleGroup(group);
        button.getStyleClass().add("toolbar-toggle-button");
        return button;
    }

    private void toggleSidebar() {
        if (splitPane.getItems().contains(sidePanel)) {
            splitPane.getItems().remove(sidePanel);
        } else {
            splitPane.getItems().add(0, sidePanel);
            splitPane.setDividerPositions(0.2);
        }
    }

    private void enterPresentationMode() {
        showError("Presentation mode not yet implemented");
    }

    private void downloadPDF() {
        if (document == null) return;
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        
        File file = fileChooser.showSaveDialog(scrollPane.getScene().getWindow());
        if (file != null) {
            try {
                document.save(file);
            } catch (Exception e) {
                showError("Error saving PDF: " + e.getMessage());
            }
        }
    }

    private void checkVisiblePagesAndLoad() {
        if (document == null) return;

        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double scrollY = scrollPane.getVvalue() * (pagesContainer.getHeight() - viewportHeight);

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            ImageView pageView = pageViews.get(i);
            if (pageView != null) {
                Bounds bounds = pageView.localToScene(pageView.getBoundsInLocal());
                boolean isVisible = bounds.intersects(0, scrollY, scrollPane.getWidth(), viewportHeight);
                
                if (isVisible && pageView.getImage() == null) {
                    loadPage(i);
                }
            }
        }
    }

    private void setupContinuousMode() {
        Platform.runLater(() -> {
            pagesContainer.getChildren().clear();
            pageViews.clear();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                ImageView pageView = new ImageView();
                pageView.setPreserveRatio(true);
                pageView.setSmooth(true);
                
                DropShadow shadow = new DropShadow();
                shadow.setRadius(10.0);
                shadow.setOffsetY(5.0);
                shadow.setColor(Color.rgb(0, 0, 0, 0.5));
                pageView.setEffect(shadow);

                StackPane pageContainer = new StackPane(pageView);
                pageContainer.setStyle("-fx-background-color: white;");
                pageContainer.setPadding(new Insets(10));
                
                pageViews.put(i, pageView);
                pagesContainer.getChildren().add(pageContainer);

                if (Math.abs(i - currentPage) <= 2) {
                    loadPage(i);
                }
            }
        });
    }

    private void loadPage(int pageNum) {
        if (!pageViews.containsKey(pageNum)) return;

        ImageView pageView = pageViews.get(pageNum);
        if (pageView.getImage() != null) return;

        Task<Image> loadTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                float dpi = 96.0f * (float)zoomFactor;
                
                BufferedImage bufferedImage = renderer.renderImageWithDPI(
                    pageNum, 
                    dpi,
                    ImageType.RGB
                );

                BufferedImage correctedImage = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
                );

                float brightness = 0.9f;
                float contrast = 1.1f;
                float gamma = 1.1f;

                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    for (int y = 0; y < bufferedImage.getHeight(); y++) {
                        int rgb = bufferedImage.getRGB(x, y);
                        
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        
                        r = (int)(Math.pow(r / 255.0, gamma) * 255 * brightness);
                        g = (int)(Math.pow(g / 255.0, gamma) * 255 * brightness);
                        b = (int)(Math.pow(b / 255.0, gamma) * 255 * brightness);
                        
                        r = (int)(((r / 255.0 - 0.5) * contrast + 0.5) * 255);
                        g = (int)(((g / 255.0 - 0.5) * contrast + 0.5) * 255);
                        b = (int)(((b / 255.0 - 0.5) * contrast + 0.5) * 255);
                        
                        r = Math.min(255, Math.max(0, r));
                        g = Math.min(255, Math.max(0, g));
                        b = Math.min(255, Math.max(0, b));
                        
                        correctedImage.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
                }

                return createFXImage(correctedImage);
            }
        };

        loadTask.setOnSucceeded(e -> {
            Image image = loadTask.getValue();
            pageView.setImage(image);
            pageView.setFitWidth(scrollPane.getViewportBounds().getWidth() - 60);
        });

        loadTask.setOnFailed(e -> {
            showError("Error loading page " + (pageNum + 1));
        });

        executorService.submit(loadTask);
    }

    private void scrollToPage(int pageNum) {
        if (pageViews.containsKey(pageNum)) {
            ImageView pageView = pageViews.get(pageNum);
            Platform.runLater(() -> {
                double totalHeight = pagesContainer.getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();
                
                Bounds pageBounds = pageView.localToScene(pageView.getBoundsInLocal());
                double pageTop = pageBounds.getMinY();
                
                scrollPane.setVvalue(pageTop / (totalHeight - viewportHeight));
            });
        }
    }

    private enum ViewMode {
        SINGLE_PAGE,
        CONTINUOUS
    }

    private void handleZoomPreset(String preset) {
        if (document == null) return;
        
        try {
            switch (preset.toLowerCase()) {
                case "auto":
                    fitToPage();
                    break;
                case "page width":
                    fitToWidth();
                    break;
                case "page fit":
                    fitToPage();
                    break;
                default:
                    String percentStr = preset.replace("%", "").trim();
                    double percent = Double.parseDouble(percentStr);
                    double newZoom = percent / 100.0;
                    if (newZoom >= 0.1 && newZoom <= 5.0) {
                        zoomFactor = newZoom;
                        updatePageDisplay();
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            zoomComboBox.setValue(String.format("%.0f%%", zoomFactor * 100));
        }
    }
} 