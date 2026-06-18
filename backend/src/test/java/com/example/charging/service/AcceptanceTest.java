package com.example.charging.service;

import com.example.charging.config.ChargingProperties;
import com.example.charging.dto.*;
import com.example.charging.entity.*;
import com.example.charging.enums.*;
import com.example.charging.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AcceptanceTest {

    @Autowired
    private ChargingRequestService requestService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private PileService pileService;
    @Autowired
    private BillService billService;
    @Autowired
    private SchedulerService schedulerService;
    @Autowired
    private FeeService feeService;
    @Autowired
    private ChargingProperties chargingProperties;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private ChargingPileRepository pileRepository;
    @Autowired
    private ChargingRequestRepository requestRepository;
    @Autowired
    private ChargingSessionRepository sessionRepository;
    @Autowired
    private BillRepository billRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ElectricityPriceRepository priceRepository;
    @Autowired
    private ChargingStationRepository stationRepository;

    private LocalDateTime currentSimTime;
    private final Map<String, Long> vehicleToReqId = new HashMap<>();
    private final Map<String, Long> vehicleToId = new HashMap<>();
    private final Map<String, Long> vehicleToUserId = new HashMap<>();

    // Simulation states
    private final Map<String, List<Long>> pileQueues = new HashMap<>(); // pileCode -> list of request IDs
    private final List<Long> waitingArea = new ArrayList<>(); // list of request IDs in waiting area
    private final Map<String, Long> activeSessions = new HashMap<>(); // pileCode -> active session ID

    static class Checkpoint {
        String time;
        String event;
        String[] piles = new String[5]; // Fast1, Fast2, Slow1, Slow2, Slow3 serving (or first line)
        String[] queue1 = new String[5]; // queue position 1
        String[] queue2 = new String[5]; // queue position 2
        String waitingArea;
        int lineNum;
    }

    @Test
    void runAcceptanceTest() throws Exception {
        // 1. Clear database and insert test metadata
        clearDatabase();
        initCoreTestData();

        // 2. Parse CSV
        Path csvPath = Paths.get("..", "tests", "resources", "作业验收用例_测试用例.csv")
                .toAbsolutePath()
                .normalize();

        List<Checkpoint> checkpoints = parseCSV(csvPath.toString());
        // Initialize simulation time to the first checkpoint's time
        Checkpoint firstCp = checkpoints.get(0);
        currentSimTime = parseSimTime(firstCp.time);

        int totalCheckpoints = checkpoints.size();
        int passed = 0;
        int failed = 0;
        Checkpoint firstFailure = null;
        List<String> mismatchReports = new ArrayList<>();

        PrintWriter reportWriter = new PrintWriter(new FileWriter("../acceptance_report.md"));
        reportWriter.println("# Acceptance Test Report");
        reportWriter.println();
        reportWriter.println("| Checkpoint | Event | Status | Details |");
        reportWriter.println("| --- | --- | --- | --- |");

        for (Checkpoint cp : checkpoints) {
            LocalDateTime targetTime = parseSimTime(cp.time);
            
            // Step 1 & 2: Advance simulation time and settle charging progress
            advanceTime(targetTime);

            // Step 3 & 4: Execute current event
            executeEvent(cp.event);
            startAssignedSessionsFromDatabase();
            syncMirrorFromDatabase();

            if ("9:00".equals(cp.time) || "9:50".equals(cp.time) || "10:00".equals(cp.time)) {
                System.out.println("DEBUG " + cp.time + " - Requests in DB:");
                requestRepository.findAll().forEach(r -> {
                    String plate = vehicleRepository.findById(r.getVehicleId()).map(Vehicle::getPlateNumber).orElse("unknown");
                    System.out.println("  Request " + plate + " (id=" + r.getId() + "): status=" + r.getStatus() + ", queueArea=" + r.getQueueArea() + ", queueNumber=" + r.getQueueNumber() + ", assignedPileId=" + r.getAssignedPileId());
                });
            }

            // Step 5 & 6: Get snapshot and compare
            String mismatch = verifyState(cp);
            if (mismatch == null) {
                passed++;
                reportWriter.printf("| %s | %s | PASSED | - |%n", cp.time, cp.event);
            } else {
                failed++;
                if (firstFailure == null) {
                    firstFailure = cp;
                }
                mismatchReports.add(mismatch);
                reportWriter.printf("| %s | %s | FAILED | %s |%n", cp.time, cp.event, mismatch.replace("\n", "<br>"));
            }
        }

        reportWriter.println();
        reportWriter.println("## Summary");
        reportWriter.println();
        reportWriter.println("- Total Checkpoints: " + totalCheckpoints);
        reportWriter.println("- Passed: " + passed);
        reportWriter.println("- Failed: " + failed);
        if (firstFailure != null) {
            reportWriter.println("- First Failure Checkpoint: " + firstFailure.time + " (" + firstFailure.event + ")");
        }
        reportWriter.close();

        System.out.println("Acceptance Test Summary:");
        System.out.println("Total: " + totalCheckpoints);
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        if (firstFailure != null) {
            System.out.println("First Failure: " + firstFailure.time + " | " + firstFailure.event);
            System.out.println("Mismatch detail:\n" + mismatchReports.get(0));
        }
        sessionRepository.findAll().stream()
                .map(ChargingSession::getEndTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(endTime -> System.out.println("Actual final session end time: " + endTime));

        assertEquals(0, failed, "Acceptance test failed with " + failed + " mismatches");
    }

    @Test
    void exportCurrentSchedulingOutput() throws Exception {
        clearDatabase();
        initCoreTestData();

        Path csvPath = Paths.get("..", "tests", "resources", "作业验收用例_测试用例.csv")
                .toAbsolutePath()
                .normalize();
        Path outputPath = Paths.get("..", "current_schedule_output.csv")
                .toAbsolutePath()
                .normalize();

        List<Checkpoint> checkpoints = parseCSV(csvPath.toString());
        currentSimTime = parseSimTime(checkpoints.get(0).time);

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8))) {
            writeCsvRow(writer, "时刻", "事件", "快充1", "快充2", "慢充1", "慢充2", "慢充3", "等候区");
            for (Checkpoint cp : checkpoints) {
                LocalDateTime targetTime = parseSimTime(cp.time);
                advanceTime(targetTime);
                executeEvent(cp.event);
                startAssignedSessionsFromDatabase();
                syncMirrorFromDatabase();

                writeCsvRow(writer,
                        cp.time,
                        cp.event,
                        getActualActiveString("FAST-1"),
                        getActualActiveString("FAST-2"),
                        getActualActiveString("SLOW-1"),
                        getActualActiveString("SLOW-2"),
                        getActualActiveString("SLOW-3"),
                        getActualWaitingString());
                writeCsvRow(writer,
                        "",
                        "",
                        getActualQueueString("FAST-1", 0),
                        getActualQueueString("FAST-2", 0),
                        getActualQueueString("SLOW-1", 0),
                        getActualQueueString("SLOW-2", 0),
                        getActualQueueString("SLOW-3", 0),
                        "");
                writeCsvRow(writer,
                        "",
                        "",
                        getActualQueueString("FAST-1", 1),
                        getActualQueueString("FAST-2", 1),
                        getActualQueueString("SLOW-1", 1),
                        getActualQueueString("SLOW-2", 1),
                        getActualQueueString("SLOW-3", 1),
                        "");
            }
        }

        System.out.println("Current scheduling output written to: " + outputPath);
    }

    private void clearDatabase() {
        sessionRepository.deleteAll();
        requestRepository.deleteAll();
        billRepository.deleteAll();
        paymentRepository.deleteAll();
        vehicleRepository.deleteAll();
        userRepository.deleteAll();
        priceRepository.deleteAll();
        pileRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private void initCoreTestData() {
        ChargingStation station = new ChargingStation();
        station.setName("张江科创充电站");
        station.setAddress("上海市浦东新区张江高科技园区");
        station.setStatus(StationStatus.AVAILABLE.name());
        station = stationRepository.save(station);
        Long stationId = station.getId();

        String fastPower = chargingProperties.getPower().getFast().toString();
        String slowPower = chargingProperties.getPower().getSlow().toString();
        String serviceFee = chargingProperties.getPrice().getService().toString();

        createPile(stationId, "FAST-1", ChargeMode.FAST, fastPower, serviceFee);
        createPile(stationId, "FAST-2", ChargeMode.FAST, fastPower, serviceFee);
        createPile(stationId, "SLOW-1", ChargeMode.SLOW, slowPower, serviceFee);
        createPile(stationId, "SLOW-2", ChargeMode.SLOW, slowPower, serviceFee);
        createPile(stationId, "SLOW-3", ChargeMode.SLOW, slowPower, serviceFee);

        createPrice(PricePeriod.VALLEY, ChargeMode.FAST, chargingProperties.getPrice().getValley().toString(), serviceFee);
        createPrice(PricePeriod.FLAT, ChargeMode.FAST, chargingProperties.getPrice().getNormal().toString(), serviceFee);
        createPrice(PricePeriod.PEAK, ChargeMode.FAST, chargingProperties.getPrice().getPeak().toString(), serviceFee);

        createPrice(PricePeriod.VALLEY, ChargeMode.SLOW, chargingProperties.getPrice().getValley().toString(), serviceFee);
        createPrice(PricePeriod.FLAT, ChargeMode.SLOW, chargingProperties.getPrice().getNormal().toString(), serviceFee);
        createPrice(PricePeriod.PEAK, ChargeMode.SLOW, chargingProperties.getPrice().getPeak().toString(), serviceFee);

        pileQueues.put("FAST-1", new ArrayList<>());
        pileQueues.put("FAST-2", new ArrayList<>());
        pileQueues.put("SLOW-1", new ArrayList<>());
        pileQueues.put("SLOW-2", new ArrayList<>());
        pileQueues.put("SLOW-3", new ArrayList<>());
    }

    private void createPile(Long stationId, String code, ChargeMode mode, String power, String serviceFee) {
        ChargingPile pile = new ChargingPile();
        pile.setStationId(stationId);
        pile.setCode(code);
        pile.setMode(mode);
        pile.setPower(new BigDecimal(power));
        pile.setStatus(ChargingPileStatus.IDLE);
        pile.setServiceFee(new BigDecimal(serviceFee));
        pileRepository.save(pile);
    }

    private void createPrice(PricePeriod period, ChargeMode mode, String chargingFee, String serviceFee) {
        ElectricityPrice price = new ElectricityPrice();
        price.setPeriod(period);
        price.setMode(mode);
        price.setChargingFee(new BigDecimal(chargingFee));
        price.setServiceFee(new BigDecimal(serviceFee));
        priceRepository.save(price);
    }

    private LocalDateTime parseSimTime(String tStr) {
        LocalDate baseDate = LocalDate.of(2026, 6, 17);
        if (tStr.contains("+1")) {
            baseDate = baseDate.plusDays(1);
            tStr = tStr.replace("+1", "").trim();
        }
        String[] parts = tStr.split(":");
        int hour = Integer.parseInt(parts[0]);
        int min = Integer.parseInt(parts[1]);
        return baseDate.atTime(hour, min);
    }

    private List<Checkpoint> parseCSV(String path) throws Exception {
        List<Checkpoint> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            List<String[]> allRows = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                allRows.add(parseCSVLine(line));
            }

            int i = 2; // skip first 2 lines
            while (i < allRows.size()) {
                String[] row1 = allRows.get(i);
                if (row1 == null || row1.length == 0 || row1[0].trim().isEmpty() || row1[0].startsWith("注：")) {
                    i++;
                    continue;
                }
                String[] row2 = (i + 1 < allRows.size()) ? allRows.get(i + 1) : new String[row1.length];
                String[] row3 = (i + 2 < allRows.size()) ? allRows.get(i + 2) : new String[row1.length];

                Checkpoint cp = new Checkpoint();
                cp.time = row1[0].trim();
                cp.event = row1[1].trim();
                cp.lineNum = i + 1;

                for (int col = 2; col <= 6; col++) {
                    cp.piles[col - 2] = getCell(row1, col);
                    cp.queue1[col - 2] = getCell(row2, col);
                    cp.queue2[col - 2] = getCell(row3, col);
                }
                cp.waitingArea = getCell(row1, 7);

                list.add(cp);
                i += 3;
            }
        }
        return list;
    }

    private String getCell(String[] row, int col) {
        if (col < row.length) {
            String val = row[col].trim();
            return val.isEmpty() ? "-" : val;
        }
        return "-";
    }

    private String[] parseCSVLine(String line) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString());
        return list.toArray(new String[0]);
    }

    private void writeCsvRow(PrintWriter writer, String... cells) {
        writer.println(Arrays.stream(cells)
                .map(this::csvCell)
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if ("-".equals(value)) {
            return value;
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void advanceTime(LocalDateTime targetTime) {
        while (currentSimTime.isBefore(targetTime)) {
            currentSimTime = currentSimTime.plusMinutes(1);
            settleChargingFor1Minute();
        }
    }

    private void settleChargingFor1Minute() {
        requestRepository.findById(1L).ifPresent(req -> {
            System.out.println("TIME " + currentSimTime + " | V1 status: " + req.getStatus() + ", charged: " + req.getChargedAmount());
        });
        List<ChargingSession> active = sessionRepository.findAll().stream()
                .filter(s -> s.getStatus() == ChargingSessionStatus.CHARGING)
                .toList();
        for (ChargingSession session : active) {
            ChargingRequest request = requestRepository.findById(session.getRequestId()).orElseThrow();
            ChargingPile pile = pileRepository.findById(session.getPileId()).orElseThrow();

            BigDecimal power = pile.getPower();
            BigDecimal charged1Min = power.divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);

            BigDecimal currentTotalCharged = request.getChargedAmount().add(session.getChargedAmount());
            BigDecimal remaining = request.getTargetAmount().subtract(currentTotalCharged);
            boolean finished = false;
            BigDecimal newSessionCharged = session.getChargedAmount().add(charged1Min);
            if (remaining.compareTo(charged1Min) <= 0) {
                newSessionCharged = session.getChargedAmount().add(remaining);
                finished = true;
            }

            session.setChargedAmount(newSessionCharged);
            sessionRepository.save(session);

            if (finished) {
                StopSessionRequest stopReq = new StopSessionRequest();
                stopReq.setChargedAmount(newSessionCharged);
                stopReq.setMockEndTime(currentSimTime);
                sessionService.stop(session.getId(), stopReq);
            }
        }

        startAssignedSessionsFromDatabase();
        syncMirrorFromDatabase();
    }

    private void startAssignedSessionsFromDatabase() {
        boolean started;
        do {
            started = false;
            List<ChargingRequest> assignedRequests = requestRepository.findAll().stream()
                    .filter(request -> request.getStatus() == ChargingRequestStatus.ASSIGNED)
                    .filter(request -> request.getAssignedPileId() != null)
                    .sorted(Comparator
                            .comparing(ChargingRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(ChargingRequest::getId))
                    .toList();
            for (ChargingRequest request : assignedRequests) {
                boolean alreadyCharging = sessionRepository
                        .findFirstByRequestIdAndStatus(request.getId(), ChargingSessionStatus.CHARGING)
                        .isPresent();
                if (alreadyCharging) {
                    continue;
                }
                ChargingPile pile = pileRepository.findById(request.getAssignedPileId()).orElseThrow();
                if (pile.getStatus() == ChargingPileStatus.FAULT || pile.getStatus() == ChargingPileStatus.OFFLINE) {
                    continue;
                }
                SessionDTO sessionDto = sessionService.start(request.getId(), pile.getId());
                ChargingSession session = sessionRepository.findById(sessionDto.getSessionId()).orElseThrow();
                session.setStartTime(currentSimTime);
                sessionRepository.save(session);
                started = true;
            }
        } while (started);
    }

    private void syncMirrorFromDatabase() {
        waitingArea.clear();
        for (List<Long> queue : pileQueues.values()) {
            queue.clear();
        }
        activeSessions.clear();

        sessionRepository.findAll().stream()
                .filter(session -> session.getStatus() == ChargingSessionStatus.CHARGING)
                .sorted(Comparator.comparing(ChargingSession::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(session -> {
                    ChargingPile pile = pileRepository.findById(session.getPileId()).orElseThrow();
                    activeSessions.put(pile.getCode(), session.getId());
                });

        requestRepository.findAll().stream()
                .filter(request -> request.getStatus() == ChargingRequestStatus.WAITING)
                .filter(request -> request.getQueueArea() == QueueArea.WAITING_AREA)
                .sorted(Comparator
                        .comparing(ChargingRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChargingRequest::getId))
                .forEach(request -> waitingArea.add(request.getId()));

        requestRepository.findAll().stream()
                .filter(request -> request.getQueueArea() == QueueArea.PILE_QUEUE)
                .filter(request -> request.getAssignedPileId() != null)
                .filter(request -> request.getStatus() == ChargingRequestStatus.WAITING
                        || request.getStatus() == ChargingRequestStatus.ASSIGNED)
                .sorted(Comparator
                        .comparing(ChargingRequest::getAssignedPileId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChargingRequest::getQueueNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChargingRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChargingRequest::getId))
                .forEach(request -> {
                    ChargingPile pile = pileRepository.findById(request.getAssignedPileId()).orElseThrow();
                    List<Long> queue = pileQueues.get(pile.getCode());
                    if (queue != null) {
                        queue.add(request.getId());
                    }
                });
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void executeEvent(String eventStr) {
        if (eventStr == null || eventStr.isEmpty() || "-".equals(eventStr) || "调度结束".equals(eventStr)) {
            return;
        }

        Pattern p = Pattern.compile("\\((A|B|C),([^,]+),([^,]+),([^\\)]+)\\)");
        Matcher m = p.matcher(eventStr);
        if (!m.find()) {
            return;
        }

        String type = m.group(1);
        String entityCode = m.group(2); // Vxx or Pxx (F1, T1 etc.)
        String modeOrAction = m.group(3); // F/T/O
        String valueStr = m.group(4); // targetAmount or fault/recovery (0/1)

        if ("A".equals(type)) {
            // Submit charging request or Cancel
            if ("O".equals(modeOrAction)) {
                // Cancel/Stop
                handleCancel(entityCode);
            } else {
                // Submit
                ChargeMode mode = "F".equals(modeOrAction) ? ChargeMode.FAST : ChargeMode.SLOW;
                BigDecimal target = new BigDecimal(valueStr);
                handleSubmit(entityCode, mode, target);
            }
        } else if ("B".equals(type)) {
            // Fault / Recovery
            String pileCode = mapEventPileCode(entityCode);
            ChargingPile pile = pileRepository.findByCode(pileCode).orElseThrow();
            if ("0".equals(valueStr)) {
                // Fault
                BigDecimal chargedInSession = BigDecimal.ZERO;
                Long sessionId = activeSessions.get(pileCode);
                if (sessionId != null) {
                    ChargingSession session = sessionRepository.findById(sessionId).orElseThrow();
                    chargedInSession = session.getChargedAmount();
                }
                pileService.markFault(pile.getId(), "fault", chargedInSession);
                activeSessions.remove(pileCode);
                if (sessionId != null) {
                    ChargingSession session = sessionRepository.findById(sessionId).orElseThrow();
                    session.setEndTime(currentSimTime);
                    sessionRepository.save(session);
                }
            } else {
                // Recovery
                pileService.recover(pile.getId());
            }
        } else if ("C".equals(type)) {
            // Modify target amount
            BigDecimal target = new BigDecimal(valueStr);
            handleModify(entityCode, target);
        }
    }

    private String mapEventPileCode(String code) {
        if ("F1".equals(code)) return "FAST-1";
        if ("F2".equals(code)) return "FAST-2";
        if ("T1".equals(code)) return "SLOW-1";
        if ("T2".equals(code)) return "SLOW-2";
        if ("T3".equals(code)) return "SLOW-3";
        return code;
    }

    private void ensureUserAndVehicle(String vName) {
        if (!vehicleToUserId.containsKey(vName)) {
            User user = new User();
            user.setUsername("user_" + vName);
            user.setPassword("123456");
            user.setPhone("13800000000");
            user.setRole("USER");
            user = userRepository.save(user);
            vehicleToUserId.put(vName, user.getId());

            Vehicle vehicle = new Vehicle();
            vehicle.setUserId(user.getId());
            vehicle.setPlateNumber(vName);
            vehicle.setModel("EV");
            vehicle.setBatteryCapacity(new BigDecimal("100.00"));
            vehicle = vehicleRepository.save(vehicle);
            vehicleToId.put(vName, vehicle.getId());
        }
    }

    private void handleSubmit(String vName, ChargeMode mode, BigDecimal target) {
        try {
            ensureUserAndVehicle(vName);
            Long userId = vehicleToUserId.get(vName);
            Long vehicleId = vehicleToId.get(vName);

            ChargingRequestSubmitRequest req = new ChargingRequestSubmitRequest();
            req.setUserId(userId);
            req.setVehicleId(vehicleId);
            req.setMode(mode);
            req.setTargetAmount(target);

            ChargingRequestDetailDTO detail = requestService.submitRequest(req);
            vehicleToReqId.put(vName, detail.getRequestId());
        } catch (Exception e) {
            System.out.println("DEBUG handleSubmit: vehicle " + vName + " submission failed (e.g. queue full / waiting area full): " + e.getMessage());
        }
    }

    private void handleCancel(String vName) {
        Long reqId = vehicleToReqId.get(vName);
        if (reqId == null) return;

        ChargingRequest request = requestRepository.findById(reqId).orElseThrow();
        Long userId = vehicleToUserId.get(vName);

        // Check where the request is
        System.out.println("DEBUG handleCancel: vName=" + vName + ", reqId=" + reqId + ", requestStatus=" + request.getStatus());
        System.out.println("DEBUG activeSessions keys: " + activeSessions.keySet());
        for (Map.Entry<String, Long> entry : activeSessions.entrySet()) {
            System.out.println("DEBUG activeSession: pile=" + entry.getKey() + ", sessionId=" + entry.getValue());
            sessionRepository.findById(entry.getValue()).ifPresent(s -> {
                System.out.println("  -> session reqId=" + s.getRequestId() + ", status=" + s.getStatus());
            });
        }
        String targetPile = null;
        for (Map.Entry<String, Long> entry : activeSessions.entrySet()) {
            ChargingSession session = sessionRepository.findById(entry.getValue()).orElseThrow();
            if (session.getRequestId().equals(reqId)) {
                targetPile = entry.getKey();
                break;
            }
        }

        System.out.println("DEBUG targetPile=" + targetPile);

        if (targetPile != null) {
            // Charging, stop session
            Long sessionId = activeSessions.remove(targetPile);
            StopSessionRequest stopReq = new StopSessionRequest();
            stopReq.setChargedAmount(request.getChargedAmount());
            stopReq.setMockEndTime(currentSimTime);
            sessionService.stop(sessionId, stopReq);
        } else {
            if (request.getStatus() == ChargingRequestStatus.WAITING || request.getStatus() == ChargingRequestStatus.ASSIGNED) {
                requestService.cancelRequest(reqId, userId);
            }
        }
    }

    private void handleModify(String vName, BigDecimal target) {
        Long reqId = vehicleToReqId.get(vName);
        if (reqId == null) return;
        Long userId = vehicleToUserId.get(vName);
        ChargingRequestModifyRequest req = new ChargingRequestModifyRequest();
        req.setTargetAmount(target);
        requestService.modifyRequest(reqId, userId, req);
    }

    private String verifyState(Checkpoint cp) {
        StringBuilder sb = new StringBuilder();
        String[] pileCodes = {"FAST-1", "FAST-2", "SLOW-1", "SLOW-2", "SLOW-3"};

        for (int i = 0; i < 5; i++) {
            String pCode = pileCodes[i];
            String pileName = new String[]{"快充1", "快充2", "慢充1", "慢充2", "慢充3"}[i];

            // 1. Active vehicle
            String actualActive = getActualActiveString(pCode);
            String expectedActive = cp.piles[i];
            if (!matchString(actualActive, expectedActive)) {
                sb.append(String.format("%s active mismatch: expected=%s, actual=%s\n", pileName, expectedActive, actualActive));
            }

            // 2. Queue[0]
            String actualQ1 = getActualQueueString(pCode, 0);
            String expectedQ1 = cp.queue1[i];
            if (!matchString(actualQ1, expectedQ1)) {
                sb.append(String.format("%s queue[0] mismatch: expected=%s, actual=%s\n", pileName, expectedQ1, actualQ1));
            }

            // 3. Queue[1]
            String actualQ2 = getActualQueueString(pCode, 1);
            String expectedQ2 = cp.queue2[i];
            if (!matchString(actualQ2, expectedQ2)) {
                sb.append(String.format("%s queue[1] mismatch: expected=%s, actual=%s\n", pileName, expectedQ2, actualQ2));
            }
        }

        // 4. Waiting Area
        String actualWaiting = getActualWaitingString();
        String expectedWaiting = cp.waitingArea;
        if (!matchString(actualWaiting, expectedWaiting)) {
            sb.append(String.format("Waiting Area mismatch: expected=%s, actual=%s\n", expectedWaiting, actualWaiting));
        }

        if (sb.length() > 0) {
            String header = String.format("[Line %d][%s][event=%s]\n", cp.lineNum, cp.time, cp.event);
            return header + sb.toString();
        }
        return null;
    }

    private String getActualActiveString(String pileCode) {
        ChargingPile pile = pileRepository.findByCode(pileCode).orElseThrow();
        if (pile.getStatus() == ChargingPileStatus.FAULT) {
            List<ChargingSession> sessions = sessionRepository.findByPileIdAndStatus(pile.getId(), ChargingSessionStatus.INTERRUPTED);
            ChargingSession interruptedSession = null;
            ChargingRequest interruptedReq = null;
            for (ChargingSession s : sessions) {
                ChargingRequest r = requestRepository.findById(s.getRequestId()).orElse(null);
                if (r != null && r.getStatus() == ChargingRequestStatus.WAITING && r.getQueueNumber() != null && r.getQueueNumber() == 0) {
                    interruptedSession = s;
                    interruptedReq = r;
                    break;
                }
            }

            if (interruptedReq != null && interruptedSession != null) {
                String vName = vehicleRepository.findById(interruptedSession.getVehicleId()).orElseThrow().getPlateNumber();
                BigDecimal totalCharged = interruptedReq.getChargedAmount();
                FeeCalcRequest calcReq = FeeCalcRequest.builder()
                        .startTime(interruptedSession.getStartTime())
                        .endTime(interruptedSession.getEndTime())
                        .energyKwh(interruptedSession.getChargedAmount())
                        .mode(pile.getMode())
                        .build();
                BigDecimal sessionFee = feeService.calculate(calcReq).getTotalFee();

                List<Bill> pastBills = billRepository.findByUserId(interruptedSession.getUserId());
                BigDecimal totalFee = sessionFee;
                for (Bill b : pastBills) {
                    if (b.getSessionId().equals(interruptedSession.getId())) continue;
                    totalFee = totalFee.add(b.getTotalFee());
                }
                return String.format("(%s,%.2f,%.2f)", vName, totalCharged, totalFee);
            }
            return "-";
        }

        Long sessionId = activeSessions.get(pileCode);
        if (sessionId == null) {
            return "-";
        }
        ChargingSession session = sessionRepository.findById(sessionId).orElseThrow();
        ChargingRequest request = requestRepository.findById(session.getRequestId()).orElseThrow();
        String vName = vehicleRepository.findById(session.getVehicleId()).orElseThrow().getPlateNumber();

        LocalDateTime endTime = session.getStatus() == ChargingSessionStatus.INTERRUPTED ? session.getEndTime() : currentSimTime;
        FeeCalcRequest calcReq = FeeCalcRequest.builder()
                .startTime(session.getStartTime())
                .endTime(endTime)
                .energyKwh(session.getChargedAmount())
                .mode(pile.getMode())
                .build();
        BigDecimal currentSessionFee = feeService.calculate(calcReq).getTotalFee();

        List<Bill> pastBills = billRepository.findByUserId(session.getUserId());
        BigDecimal totalFee = currentSessionFee;
        for (Bill b : pastBills) {
            if (b.getSessionId().equals(session.getId())) continue;
            totalFee = totalFee.add(b.getTotalFee());
        }

        BigDecimal totalCharged = request.getChargedAmount().add(session.getChargedAmount());

        return String.format("(%s,%.2f,%.2f)", vName, totalCharged, totalFee);
    }

    private String getActualQueueString(String pileCode, int index) {
        List<Long> q = pileQueues.get(pileCode);
        if (q == null || index >= q.size()) {
            return "-";
        }
        Long reqId = q.get(index);
        ChargingRequest request = requestRepository.findById(reqId).orElseThrow();
        String vName = vehicleRepository.findById(request.getVehicleId()).orElseThrow().getPlateNumber();

        List<Bill> pastBills = billRepository.findByUserId(request.getUserId());
        BigDecimal totalFee = BigDecimal.ZERO;
        for (Bill b : pastBills) {
            totalFee = totalFee.add(b.getTotalFee());
        }

        List<ChargingSession> sessions = sessionRepository.findByRequestId(reqId);
        ChargingPile pile = pileRepository.findByCode(pileCode).orElseThrow();
        for (ChargingSession s : sessions) {
            if (s.getStatus() == ChargingSessionStatus.INTERRUPTED) {
                boolean billed = billRepository.findAll().stream().anyMatch(b -> b.getSessionId().equals(s.getId()));
                if (!billed) {
                    FeeCalcRequest calcReq = FeeCalcRequest.builder()
                            .startTime(s.getStartTime())
                            .endTime(s.getEndTime())
                            .energyKwh(s.getChargedAmount())
                            .mode(pile.getMode())
                            .build();
                    BigDecimal sessionFee = feeService.calculate(calcReq).getTotalFee();
                    totalFee = totalFee.add(sessionFee);
                }
            }
        }

        return String.format("(%s,%.2f,%.2f)", vName, request.getChargedAmount(), totalFee);
    }

    private String getActualWaitingString() {
        if (waitingArea.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Long reqId : waitingArea) {
            ChargingRequest request = requestRepository.findById(reqId).orElseThrow();
            String vName = vehicleRepository.findById(request.getVehicleId()).orElseThrow().getPlateNumber();
            String modeCode = request.getMode() == ChargeMode.FAST ? "F" : "T";
            sb.append(String.format("(%s,%s,%.2f)-", vName, modeCode, request.getTargetAmount()));
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private boolean matchString(String act, String exp) {
        if (act == null || act.trim().isEmpty() || "-".equals(act)) {
            return exp == null || exp.trim().isEmpty() || "-".equals(exp);
        }
        if (exp == null || exp.trim().isEmpty() || "-".equals(exp)) {
            return false;
        }

        String aClean = act.trim().replace(" ", "");
        String eClean = exp.trim().replace(" ", "");
        return aClean.equals(eClean);
    }
}
