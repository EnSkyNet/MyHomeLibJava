package org.myhomelib.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;

public class MainFXApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        String fxmlResourcePath = "org/myhomelib/ui/view/MainView.fxml";
        URL fxmlLocation = null;

        // Варіант 1: Через контекст завантажувача класів поточного потоку
        try {
            fxmlLocation = Thread.currentThread().getContextClassLoader().getResource(fxmlResourcePath);
        } catch (Exception ignored) {}

        // Варіант 2: Через завантажувач класу додатка
        if (fxmlLocation == null) {
            fxmlLocation = MainFXApp.class.getResource("/" + fxmlResourcePath);
        }

        // Варіант 3 (Файловий РЕЗЕРВНИЙ Fallback): Якщо Maven не скопіював ресурси, беремо файл напряму з диска
        if (fxmlLocation == null) {
            System.out.println("[ПОПЕРЕДЖЕННЯ] Maven Classpath не містить FXML. Спроба прямого зчитування з файлової системи...");

            // Перевіряємо два можливі фізичні шляхи розташування проекту
            File directFile = new File("src/main/resources/" + fxmlResourcePath);
            if (!directFile.exists()) {
                directFile = new File("MyHomeLibJava/src/main/resources/" + fxmlResourcePath);
            }

            if (directFile.exists()) {
                fxmlLocation = directFile.toURI().toURL();
                System.out.println("[INFO] Знайдено резервний файл на диску: " + fxmlLocation.toExternalForm());
            }
        }

        // Кінцева перевірка перед ініціалізацією
        if (fxmlLocation == null) {
            System.err.println("\n=========================================================================");
            System.err.println("[КРИТИЧНА ПОМИЛКА ЗБІРКИ] Жоден метод пошуку ресурсу не дав результату!");
            System.err.println("Файл MainView.fxml відсутній як у збірці target/, так і в папці src/main/resources/");
            System.err.println("Поточна робоча директорія програми: " + new File(".").getAbsolutePath());
            System.err.println("=========================================================================\n");
            throw new java.io.FileNotFoundException("FXML файл не знайдено за жодним із шляхів.");
        }

        System.out.println("[INFO] Остаточний шлях завантаження FXML: " + fxmlLocation.toExternalForm());

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        primaryStage.setTitle("MyHomeLibJava - Паттерн MVVM [800 000 книг]");
        primaryStage.setScene(new Scene(root, 1150, 720));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}