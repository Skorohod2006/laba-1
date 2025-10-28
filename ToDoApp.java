import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

enum TaskPriority {
    HIGH("1", "высокий"), 
    MEDIUM("2", "средний"), 
    LOW("3", "низкий");

    private final String number;
    private final String russianName;

    TaskPriority(String number, String russianName) {
        this.number = number;
        this.russianName = russianName;
    }

    public String getNumber() {
        return number;
    }

    public String getRussianName() {
        return russianName;
    }

    public static TaskPriority fromNumber(String number) {
        for (TaskPriority priority : values()) {
            if (priority.getNumber().equals(number)) {
                return priority;
            }
        }
        return MEDIUM;
    }

    @Override
    public String toString() {
        return russianName;
    }
}

class ToDoItem {
    private String taskId;
    private String taskDescription;
    private LocalDate deadline;
    private TaskPriority priorityLevel;
    private boolean completed;
    
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter STORAGE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ToDoItem(String taskId, String taskDescription, LocalDate deadline, TaskPriority priorityLevel, boolean completed) {
        this.taskId = taskId;
        this.taskDescription = taskDescription;
        this.deadline = deadline;
        this.priorityLevel = priorityLevel;
        this.completed = completed;
    }

    public String getTaskId() { return taskId; }
    public String getTaskDescription() { return taskDescription; }
    public LocalDate getDeadline() { return deadline; }
    public TaskPriority getPriorityLevel() { return priorityLevel; }
    public boolean isCompleted() { return completed; }

    public void setTaskDescription(String description) { this.taskDescription = description; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public void setPriorityLevel(TaskPriority priority) { this.priorityLevel = priority; }
    public void setCompleted(boolean status) { completed = status; }

    // формат для хранения в файле
    public String toStorageFormat() {
        String priorityNumber = priorityLevel.getNumber();
        String datePart = deadline != null ? deadline.format(STORAGE_FORMATTER) : "";
        String statusSymbol = completed ? "+" : "-";
        return statusSymbol + " " + priorityNumber + " " + taskDescription + ": " + datePart;
    }

    @Override
    public String toString() {
        String statusIndicator = completed ? "[✓]" : "[✗]";
        String displayDate = (deadline != null) ? deadline.format(DATE_FORMATTER) : "без сроков";
        return String.format("%s (ID: %s) | приоритет: %s | срок: %s | описание: %s",
                statusIndicator, taskId, priorityLevel.getRussianName(), displayDate, taskDescription);
    }

    // создание объекта из строки файла
    public static ToDoItem fromStorageFormat(String storageString) {
        try {
            if ((!storageString.startsWith("+ ") && !storageString.startsWith("- ")) || !storageString.contains(": ")) {
                System.err.println("неверный формат строки: " + storageString);
                return null;
            }
            
            boolean isCompleted = storageString.startsWith("+ ");
            
            String content = storageString.substring(2);
            String[] mainParts = content.split(": ", 2);
            if (mainParts.length != 2) {
                System.err.println("неверный формат строки: " + storageString);
                return null;
            }
            
            String beforeColon = mainParts[0];
            String dateStr = mainParts[1];
            
            String[] priorityAndDesc = beforeColon.split(" ", 2);
            if (priorityAndDesc.length != 2) {
                System.err.println("неверный формат строки: " + storageString);
                return null;
            }
            
            String priorityNumber = priorityAndDesc[0];
            String description = priorityAndDesc[1];
            
            TaskPriority priority = TaskPriority.fromNumber(priorityNumber);
            
            LocalDate dueDate = null;
            if (!dateStr.isEmpty()) {
                dueDate = LocalDate.parse(dateStr, STORAGE_FORMATTER);
            }
            
            String id = UUID.randomUUID().toString().substring(0, 8);
            
            return new ToDoItem(id, description, dueDate, priority, isCompleted);
            
        } catch (DateTimeParseException e) {
            System.err.println("ошибка парсинга даты: " + storageString + " - " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("ошибка парсинга: " + storageString + " - " + e.getMessage());
            return null;
        }
    }
}

class TaskManager {
    private List<ToDoItem> itemList;
    private final String filePath;

    public TaskManager(String filePath) {
        this.filePath = filePath;
        this.itemList = new ArrayList<>();
        loadTasks();
    }

    // добавление новой задачи
    public void addNewTask(String description, LocalDate deadline, TaskPriority priority) {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        } while (getTaskById(id).isPresent());

        itemList.add(new ToDoItem(id, description, deadline, priority, false));
        System.out.println("задача добавлена. (ID: " + id + ")");
        saveTasks();
    }

    // обновление существующей задачи
    public void updateTask(ToDoItem item, String description, LocalDate deadline, TaskPriority priority, Boolean isCompleted) {
        if (description != null && !description.trim().isEmpty()) {
            item.setTaskDescription(description.trim());
        }
        if (deadline != null) {
            item.setDeadline(deadline);
        }
        if (priority != null) {
            item.setPriorityLevel(priority);
        }
        if (isCompleted != null) {
            item.setCompleted(isCompleted);
        }
        System.out.println("задача с ID " + item.getTaskId() + " обновлена.");
        saveTasks();
    }

    public boolean removeTask(String taskId) {
        boolean isRemoved = itemList.removeIf(item -> item.getTaskId().equals(taskId));
        System.out.println(isRemoved ? "задача с ID " + taskId + " удалена." : "задача не найдена.");
        if (isRemoved) saveTasks();
        return isRemoved;
    }

    public Optional<ToDoItem> getTaskById(String id) {
        return itemList.stream()
                .filter(item -> item.getTaskId().equals(id))
                .findFirst();
    }

    public void showAllTasks() {
        if (itemList.isEmpty()) {
            System.out.println("список задач пуст.");
            return;
        }
        System.out.println("\n--- все задачи ---");
  
        List<ToDoItem> sortedList = new ArrayList<>(itemList);
        sortedList.sort((task1, task2) -> {
            if (task1.isCompleted() && !task2.isCompleted()) return 1;
            if (!task1.isCompleted() && task2.isCompleted()) return -1;
            return 0;
        });
        sortedList.forEach(System.out::println);
        System.out.println("-------------------\n");
    }

    // сортировка по дате выполнения
    public void sortByDeadline() {
        itemList.sort((task1, task2) -> {
            if (task1.isCompleted() && !task2.isCompleted()) return 1;
            if (!task1.isCompleted() && task2.isCompleted()) return -1;
            
            if (task1.getDeadline() == null && task2.getDeadline() == null) return 0;
            if (task1.getDeadline() == null) return 1;
            if (task2.getDeadline() == null) return -1;
            return task1.getDeadline().compareTo(task2.getDeadline());
        });
        System.out.println("задачи отсортированы по срокам выполнения.");
        saveTasks();
    }

    // сортировка по приоритету
    public void sortByPriority() {
        itemList.sort((task1, task2) -> {
            if (task1.isCompleted() && !task2.isCompleted()) return 1;
            if (!task1.isCompleted() && task2.isCompleted()) return -1;
            
            return task1.getPriorityLevel().compareTo(task2.getPriorityLevel());
        });
        System.out.println("задачи отсортированы по приоритетам.");
        saveTasks();
    }

    // поиск задач по ключевому слову
    public List<ToDoItem> searchByKeyword(String keyword) {
        String lowerCaseKeyword = keyword.toLowerCase();
        return itemList.stream()
                .filter(item -> item.getTaskDescription().toLowerCase().contains(lowerCaseKeyword))
                .collect(Collectors.toList());
    }

    public List<ToDoItem> searchByCompletionStatus(boolean completed) {
        return itemList.stream()
                .filter(item -> item.isCompleted() == completed)
                .collect(Collectors.toList());
    }

    public List<ToDoItem> searchByPriority(TaskPriority priority) {
        return itemList.stream()
                .filter(item -> item.getPriorityLevel() == priority)
                .collect(Collectors.toList());
    }

    // сохранение задач в файл
    private void saveTasks() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            for (ToDoItem item : itemList) {
                writer.println(item.toStorageFormat());
            }
        } catch (IOException e) {
            System.err.println("ошибка сохранения: " + filePath + " - " + e.getMessage());
        }
    }

    // загрузка задач из файла
    private void loadTasks() {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            System.out.println("файл не найден, создан новый список.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                ToDoItem item = ToDoItem.fromStorageFormat(line);
                if (item != null) {
                    itemList.add(item);
                } else {
                    System.err.println("пропущена строка " + lineNumber);
                }
            }
            System.out.println("задачи загружены из " + filePath);
        } catch (FileNotFoundException e) {
            System.err.println("файл не найден: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("ошибка загрузки: " + e.getMessage());
        }
    }
}

public class ToDoApp {
    private static final String DATA_FILE_PATH = "tasks.txt";
    private final TaskManager taskManager;
    private final Scanner scanner;

    private static final int ADD = 1;
    private static final int EDIT = 2;
    private static final int DELETE = 3;
    private static final int MARK_COMPLETE = 4;
    private static final int SHOW_ALL = 5;
    private static final int SORT_OPTIONS = 6;
    private static final int SEARCH_OPTIONS = 7;
    private static final int EXIT_APP = 0;

    private static final int SORT_BY_DATE_OPTION = 1;
    private static final int SORT_BY_PRIORITY_OPTION = 2;
    private static final int BACK_OPTION = 0;

    private static final int SEARCH_BY_DESC_OPTION = 1;
    private static final int SEARCH_BY_STATUS_OPTION = 2;
    private static final int SEARCH_BY_PRIORITY_OPTION = 3;

    public ToDoApp() {
        taskManager = new TaskManager(DATA_FILE_PATH);
        scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        ToDoApp app = new ToDoApp();
        app.start();
    }

    // основной цикл работы приложения
    public void start() {
        System.out.println("добро пожаловать в to-do list!");
        int userSelection;
        do {
            showMainMenu();
            userSelection = getUserInput("выберите опцию: ");

            try {
                processMenuSelection(userSelection);
            } catch (Exception e) {
                System.out.println("ошибка: " + e.getMessage());
            }
            System.out.println();
        } while (userSelection != EXIT_APP);

        System.out.println("до свидания!");
        scanner.close();
    }

    private void showMainMenu() {
        System.out.println("========== главное меню ==========");
        System.out.println(ADD + ". добавить задачу");
        System.out.println(EDIT + ". редактировать задачу");
        System.out.println(DELETE + ". удалить задачу");
        System.out.println(MARK_COMPLETE + ". изменить статус выполнения");
        System.out.println(SHOW_ALL + ". показать все задачи");
        System.out.println(SORT_OPTIONS + ". настройки сортировки");
        System.out.println(SEARCH_OPTIONS + ". настройки поиска");
        System.out.println(EXIT_APP + ". выход");
        System.out.println("==================================");
    }

    private void processMenuSelection(int choice) {
        switch (choice) {
            case ADD -> createTask();
            case EDIT -> modifyTask();
            case DELETE -> removeTask();
            case MARK_COMPLETE -> toggleTaskStatus();
            case SHOW_ALL -> taskManager.showAllTasks();
            case SORT_OPTIONS -> showSortMenu();
            case SEARCH_OPTIONS -> showSearchMenu();
            case EXIT_APP -> {}
            default -> System.out.println("некорректный выбор.");
        }
    }

    // создание новой задачи
    private void createTask() {
        System.out.print("введите описание задачи: ");
        String description = scanner.nextLine().trim();

        if (description.isEmpty()) {
            System.out.println("описание не может быть пустым.");
            return;
        }

        LocalDate dueDate = null;
        System.out.print("введите срок выполнения (дд.мм.гггг) или оставьте пустым: ");
        String inputDate = scanner.nextLine().trim();
        if (!inputDate.isEmpty()) {
            try {
                dueDate = LocalDate.parse(inputDate, ToDoItem.DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("некорректный формат даты. используйте формат дд.мм.гггг");
            }
        }

        TaskPriority priority = getTaskPriority();
        taskManager.addNewTask(description, dueDate, priority);
    }

    // редактирование существующей задачи
    private void modifyTask() {
        taskManager.showAllTasks();
        System.out.print("введите ID задачи для редактирования: ");
        String taskId = scanner.nextLine().trim();

        Optional<ToDoItem> taskOpt = taskManager.getTaskById(taskId);
        if (taskOpt.isEmpty()) {
            System.out.println("задача не найдена.");
            return;
        }

        ToDoItem task = taskOpt.get();
        System.out.println("редактирование: " + task);

        System.out.print("новое описание (пусто - не менять): ");
        String newDescription = scanner.nextLine().trim();

        LocalDate newDueDate = null;
        System.out.print("новый срок (дд.мм.гггг, пусто - не менять): ");
        String newDateInput = scanner.nextLine().trim();
        if (!newDateInput.isEmpty()) {
            try {
                newDueDate = LocalDate.parse(newDateInput, ToDoItem.DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("некорректный формат даты. используйте формат дд.мм.гггг");
            }
        }

        System.out.print("изменить приоритет? (+/-): ");
        TaskPriority newPriority = null;
        if (scanner.nextLine().trim().equalsIgnoreCase("+")) {
            newPriority = getTaskPriority();
        }

        System.out.print("изменить статус выполнения? (+/-): ");
        Boolean newStatus = null;
        if (scanner.nextLine().trim().equalsIgnoreCase("+")) {
            System.out.print("задача выполнена? (+/-): ");
            newStatus = scanner.nextLine().trim().equalsIgnoreCase("+");
        }

        taskManager.updateTask(task, newDescription, newDueDate, newPriority, newStatus);
    }

    private void removeTask() {
        taskManager.showAllTasks();
        System.out.print("введите ID задачи для удаления: ");
        String taskId = scanner.nextLine().trim();
        taskManager.removeTask(taskId);
    }

    // изменение статуса выполнения задачи
    private void toggleTaskStatus() {
        taskManager.showAllTasks();
        System.out.print("введите ID задачи для изменения статуса: ");
        String taskId = scanner.nextLine().trim();

        Optional<ToDoItem> taskOpt = taskManager.getTaskById(taskId);
        if (taskOpt.isEmpty()) {
            System.out.println("задача не найдена.");
            return;
        }

        ToDoItem task = taskOpt.get();
        boolean newStatus = !task.isCompleted();
        taskManager.updateTask(task, null, null, null, newStatus);
        System.out.println("статус изменен на: " + (newStatus ? "выполнена" : "не выполнена"));
    }

    // меню сортировки
    private void showSortMenu() {
        int choice;
        do {
            System.out.println("\n--- меню сортировки ---");
            System.out.println(SORT_BY_DATE_OPTION + ". по дате");
            System.out.println(SORT_BY_PRIORITY_OPTION + ". по приоритету");
            System.out.println(BACK_OPTION + ". назад");
            System.out.println("-----------------------");

            choice = getUserInput("выберите опцию: ");

            switch (choice) {
                case SORT_BY_DATE_OPTION -> {
                    taskManager.sortByDeadline();
                    taskManager.showAllTasks();
                    return;
                }
                case SORT_BY_PRIORITY_OPTION -> {
                    taskManager.sortByPriority();
                    taskManager.showAllTasks();
                    return;
                }
                case BACK_OPTION -> {}
                default -> System.out.println("некорректный выбор.");
            }
        } while (choice != BACK_OPTION);
    }

    // меню поиска
    private void showSearchMenu() {
        int choice;
        do {
            System.out.println("\n--- меню поиска ---");
            System.out.println(SEARCH_BY_DESC_OPTION + ". поиск по описанию");
            System.out.println(SEARCH_BY_STATUS_OPTION + ". поиск по статусу");
            System.out.println(SEARCH_BY_PRIORITY_OPTION + ". поиск по приоритету");
            System.out.println(BACK_OPTION + ". назад");
            System.out.println("-------------------");

            choice = getUserInput("выберите опцию: ");

            switch (choice) {
                case SEARCH_BY_DESC_OPTION -> {
                    searchByDescription();
                    return;
                }
                case SEARCH_BY_STATUS_OPTION -> {
                    searchByCompletionStatus();
                    return;
                }
                case SEARCH_BY_PRIORITY_OPTION -> {
                    searchByTaskPriority();
                    return;
                }
                case BACK_OPTION -> {}
                default -> System.out.println("некорректный выбор.");
            }
        } while (choice != BACK_OPTION);
    }

    private void searchByDescription() {
        System.out.print("введите ключевое слово: ");
        String keyword = scanner.nextLine().trim();
        List<ToDoItem> foundTasks = taskManager.searchByKeyword(keyword);
        showSearchResults(foundTasks, "результаты поиска: '" + keyword + "'");
    }

    private void searchByCompletionStatus() {
        System.out.print("показать выполненные? (+/-): ");
        boolean completedStatus = scanner.nextLine().trim().equalsIgnoreCase("+");
        List<ToDoItem> foundTasks = taskManager.searchByCompletionStatus(completedStatus);
        showSearchResults(foundTasks, completedStatus ? "выполненные задачи" : "невыполненные задачи");
    }

    private void searchByTaskPriority() {
        TaskPriority priority = getTaskPriority();
        List<ToDoItem> foundTasks = taskManager.searchByPriority(priority);
        showSearchResults(foundTasks, "задачи с приоритетом " + priority.getRussianName());
    }

    // отображение результатов поиска
    private void showSearchResults(List<ToDoItem> results, String title) {
        if (results.isEmpty()) {
            System.out.println("задачи не найдены.");
            return;
        }
        System.out.println("\n--- " + title + " ---");
        results.forEach(System.out::println);
        System.out.println("--------------------------------\n");
    }

    // выбор приоритета задачи
    private TaskPriority getTaskPriority() {
        while (true) {
            System.out.println("выберите приоритет:");
            System.out.println("1 - высокий");
            System.out.println("2 - средний"); 
            System.out.println("3 - низкий");
            System.out.print("ваш выбор: ");
            
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1" -> { 
                    System.out.println("выбран высокий приоритет");
                    return TaskPriority.HIGH; 
                }
                case "2" -> { 
                    System.out.println("выбран средний приоритет");
                    return TaskPriority.MEDIUM; 
                }
                case "3" -> { 
                    System.out.println("выбран низкий приоритет");
                    return TaskPriority.LOW; 
                }
                default -> System.out.println("неверный ввод. введите 1, 2 или 3.");
            }
        }
    }

    // ввод числового значения
    private int getUserInput(String prompt) {
        System.out.print(prompt);
        while (true) {
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("введите число: ");
            }
        }
    }
}
