package FX;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Y {

    private static volatile boolean running = true;
    private static volatile BigDecimal currentPi = BigDecimal.ZERO;
    private static volatile int currentPrecision = 0;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=================================================");
        System.out.println("      拉马努金公式高精度圆周率计算器");
        System.out.println("=================================================");
        System.out.println("请选择分配给本程序的最大内存：");
        System.out.println("  输入 1  ->  1 GB (最高计算 50,000 位)");
        System.out.println("  输入 2  ->  2 GB (最高计算 100,000 位)");
        System.out.println("  输入 4  ->  4 GB (最高计算 200,000 位)");
        System.out.println("  输入 8  ->  8 GB (最高计算 400,000 位)");
        System.out.println("-------------------------------------------------");
        System.out.print("请输入数字并回车: ");

        int memoryGB = 1;
        if (scanner.hasNextInt()) {
            int input = scanner.nextInt();
            if (input > 0) memoryGB = input;
        }
        scanner.nextLine();

        int maxPrecision = memoryGB * 50000;
        int maxIterations = (int) Math.ceil(maxPrecision / 8.0) + 2;

        System.out.println("\n已设定内存限制: " + memoryGB + " GB");
        System.out.println("最高安全计算精度: " + maxPrecision + " 位");
        System.out.println("\n【计算已启动，随时按下回车键即可停止并保存结果】\n");

        Thread calculatorThread = new Thread(new PiCalculatorTask(maxPrecision, maxIterations));
        calculatorThread.start();

        scanner.nextLine();
        System.out.println("\n>>> 收到停止指令，正在保存当前计算结果...");
        running = false;

        try {
            calculatorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        saveResultToExeDirectory();

        System.out.println("\n=================================================");
        System.out.println("按回车键退出程序...");
        scanner.nextLine();
        scanner.close();
    }

    private static void saveResultToExeDirectory() {
        if (currentPi.equals(BigDecimal.ZERO)) {
            System.out.println("尚未计算出有效结果，未保存文件。");
            return;
        }
        String piString = currentPi.toPlainString();
        String exeDir = getExecutableDirectory();
        String fileName = exeDir + File.separator + "pi_result.txt";
        Path filePath = Paths.get(fileName);
        try {
            Files.writeString(filePath, piString);
            System.out.println("✅ 保存成功！");
            System.out.println("最终精确位数: 小数点后 " + currentPrecision + " 位");
            System.out.println("文件位置: " + filePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 保存文件失败！");
            System.err.println("错误原因: " + e.getMessage());
            System.err.println("💡 请尝试将 exe 移动到桌面或 D 盘等非系统目录下再次运行。");
        }
    }

    private static String getExecutableDirectory() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command != null) {
                File exeFile = new File(command);
                if (exeFile.exists()) {
                    return exeFile.getParent();
                }
            }
        } catch (Exception e) { }
        return System.getProperty("user.dir");
    }

    static class PiCalculatorTask implements Runnable {
        private final int maxPrecision;
        private final int maxIterations;

        public PiCalculatorTask(int maxPrecision, int maxIterations) {
            this.maxPrecision = maxPrecision;
            this.maxIterations = maxIterations;
        }

        @Override
        public void run() {
            int calcPrecision = maxPrecision + 20;
            MathContext mc = new MathContext(calcPrecision, RoundingMode.HALF_UP);

            BigDecimal sqrt2 = BigDecimal.valueOf(2).sqrt(mc);
            BigDecimal constantFactor = sqrt2.multiply(BigDecimal.valueOf(2), mc)
                    .divide(BigDecimal.valueOf(9801), mc);

            // ====== 滚动乘积，避免重复计算 ======
            BigInteger A = BigInteger.ONE;       // (4n)!
            BigInteger B = BigInteger.ONE;       // (n!)^4
            BigInteger C = BigInteger.ONE;       // 396^(4n)
            BigInteger P396_4 = BigInteger.valueOf(396).pow(4); // 396^4 常量

            BigDecimal sum = BigDecimal.ZERO;

            for (int n = 0; n < maxIterations && running; n++) {
                if (n > 0) {
                    // 增量更新 A: A_n = A_{n-1} * (4n-3)(4n-2)(4n-1)(4n)
                    long n4 = 4L * n;
                    BigInteger incA = BigInteger.valueOf(n4 - 3)
                            .multiply(BigInteger.valueOf(n4 - 2))
                            .multiply(BigInteger.valueOf(n4 - 1))
                            .multiply(BigInteger.valueOf(n4));
                    A = A.multiply(incA);

                    // 增量更新 B: B_n = B_{n-1} * n^4
                    BigInteger nB = BigInteger.valueOf(n);
                    B = B.multiply(nB.pow(4));

                    // 增量更新 C: C_n = C_{n-1} * 396^4
                    C = C.multiply(P396_4);
                }

                // 计算第 n 项：t_n = A * (1103 + 26390n) / (B * C)
                BigInteger numerator = A.multiply(BigInteger.valueOf(1103L + 26390L * n));
                BigInteger denominator = B.multiply(C);

                BigDecimal term = new BigDecimal(numerator).divide(new BigDecimal(denominator), mc);
                sum = sum.add(term, mc);

                // 定期刷新进度（每 10 项刷新一次）
                if (n % 10 == 0 || n == maxIterations - 1) {
                    updateProgress(n, sum, constantFactor, mc);
                }
            }

            // 循环退出后再刷一次，确保 currentPi 为最终值
            updateProgress(maxIterations - 1, sum, constantFactor, mc);

            if (running) {
                System.out.println("\n\n已达到设定的内存安全上限 (" + maxPrecision + " 位)，自动停止计算。");
                running = false;
            }
        }

        private void updateProgress(int n, BigDecimal sum, BigDecimal constantFactor, MathContext mc) {
            // 用当前的和算出 π
            BigDecimal pi = BigDecimal.ONE.divide(constantFactor.multiply(sum, mc), mc);
            int precision = Math.min((n + 1) * 8, maxPrecision);
            currentPi = pi.setScale(precision, RoundingMode.HALF_UP);
            currentPrecision = precision;

            // 画进度条
            int percent = (int) (((double) (n + 1) / maxIterations) * 100);
            int barLength = 30;
            int filled = (int) (((double) (n + 1) / maxIterations) * barLength);
            StringBuilder bar = new StringBuilder("[");
            for (int i = 0; i < barLength; i++) {
                if (i < filled) bar.append("=");
                else if (i == filled) bar.append(">");
                else bar.append(" ");
            }
            bar.append("]");

            System.out.print("\r进度: " + bar + " " + percent + "% | 已算: " + precision + " 位 | 项数: " + n + "  ");
        }
    }
}