package edu.duke.cs.jflap.gui.action;

import edu.duke.cs.jflap.automata.Automaton;
import edu.duke.cs.jflap.file.XMLCodec;
import edu.duke.cs.jflap.automata.AutomatonSimulator;
import edu.duke.cs.jflap.automata.Configuration;
import edu.duke.cs.jflap.automata.SimulatorFactory;
import edu.duke.cs.jflap.automata.mealy.MealyConfiguration;
import edu.duke.cs.jflap.automata.mealy.MealyMachine;
import edu.duke.cs.jflap.automata.mealy.MooreMachine;
import edu.duke.cs.jflap.automata.turing.TuringMachine;
import edu.duke.cs.jflap.automata.fsa.FSAConfiguration;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.swing.filechooser.FileNameExtensionFilter;

public class CorrectionAction extends AutomatonAction {

    public CorrectionAction() {
        super("Correction", null);
    }

    public CorrectionAction(String string, Icon icon){
        super(string, icon);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        performAction();
    }

    private void performAction() {
        File[] jflapFiles = selectJFLAPFiles();
        if (jflapFiles == null) return;

        File responseFile = selectResponseFile();
        if (responseFile == null) return;

        processCorrection(jflapFiles, responseFile);
    }

    private File[] selectJFLAPFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("JFLAP Files", "jff"));

        int returnValue = fileChooser.showOpenDialog(null);
        if (returnValue != JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(null, "Nenhum arquivo JFLAP selecionado!", "Erro", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        return fileChooser.getSelectedFiles();
    }

    private File selectResponseFile() {
        JFileChooser responseChooser = new JFileChooser();
        responseChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
        responseChooser.setDialogTitle("Selecione o arquivo de resposta");

        int responseValue = responseChooser.showOpenDialog(null);
        if (responseValue != JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(null, "Nenhum arquivo de resposta selecionado!", "Erro", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        return responseChooser.getSelectedFile();
    }

    private void processCorrection(File[] jflapFiles, File responseFile) {
        try {
            for (File jflapFile : jflapFiles) {
                Automaton automaton = loadAutomatonFromJFLAP(jflapFile);
                if (automaton != null) {
                    processAutomaton(automaton, responseFile);
                } else {
                    System.out.println("Erro ao carregar o autômato do arquivo JFLAP: " + jflapFile.getName());
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro ao processar os arquivos!", "Erro", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private Automaton loadAutomatonFromJFLAP(File jflapFile) {
        try {
            XMLCodec reader = new XMLCodec();
            return (Automaton) reader.decode(jflapFile, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void processAutomaton(Automaton automaton, File responseFile) {
        try (Scanner scanner = new Scanner(responseFile)) {
            int responseIndex = 0;
    
            // Obtém as entradas do autômato
            String[] inputs = getInputsForAutomaton(automaton, responseFile);
    
            while (scanner.hasNextLine()) {
                // Verifica se o índice de resposta está dentro do limite das entradas
                if (responseIndex >= inputs.length) {
                    System.out.println("Aviso: Número de entradas excedido. Ignorando entradas extras.");
                    break;
                }
    
                String expectedOutput = scanner.nextLine();
                AutomatonSimulator simulator = SimulatorFactory.getSimulator(automaton);
                Configuration[] configs = simulator.getInitialConfigurations(inputs[responseIndex]);
    
                List<Configuration> associated = new ArrayList<>();
                int result = handleInput(automaton, simulator, configs, inputs[responseIndex], associated);
    
                String actualOutput = getAutomatonOutput(automaton, associated);
    
                if (expectedOutput.equals(actualOutput)) {
                    System.out.println("Resposta correta para a entrada: " + inputs[responseIndex]);
                } else {
                    System.out.println("Resposta incorreta para a entrada: " + inputs[responseIndex]);
                    System.out.println("Saída esperada: " + expectedOutput);
                    System.out.println("Saída obtida: " + actualOutput);
                }
    
                responseIndex++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }    

    private String getAutomatonOutput(Automaton automaton, List<Configuration> associated) {
    if (automaton instanceof MealyMachine) {
        // Se for uma máquina de Mealy, pega a saída de MealyConfiguration
        MealyConfiguration con = (MealyConfiguration) associated.get(0);
        return con.getOutput();
    } else if (automaton instanceof TuringMachine) {
        // Se for uma máquina de Turing, a saída pode ser "Aceito" ou "Rejeitado", dependendo do estado final
        Configuration con = associated.get(0); // Assume que a primeira configuração é a final
        return con.isAccept() ? "Aceito" : "Rejeitado";
    } else if (automaton instanceof Automaton) {
        // Se for um autômato genérico, a saída pode ser "Aceito" ou "Rejeitado", dependendo do estado final
        Configuration con = associated.get(0); // Assume que a primeira configuração é a final
        return con.isAccept() ? "Aceito" : "Rejeitado";
    } else {
        // Se o autômato não for um tipo tratado, retorne uma mensagem padrão
        return "Saída não implementada para esse tipo de autômato";
    }
}


    private String[] getInputsForAutomaton(Automaton automaton, File responseFile) {
        List<String> inputs = new ArrayList<>();
        try (Scanner scanner = new Scanner(responseFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    inputs.add(line);  // Adiciona cada linha do arquivo como uma entrada
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return inputs.toArray(new String[0]);  // Retorna o array de entradas
    }

    protected int handleInput(Automaton automaton, AutomatonSimulator simulator, Configuration[] configs, Object initialInput, List<Configuration> associatedConfigurations) {
        int numberGenerated = 0;
        int warningGenerated = 500;
        Configuration lastConsidered = configs[configs.length - 1];
        while (configs.length > 0) {
            numberGenerated += configs.length;
            if (numberGenerated >= warningGenerated) {
                while (numberGenerated >= warningGenerated)
                    warningGenerated *= 2;
            }
            ArrayList<Configuration> next = new ArrayList<>();
            for (Configuration config : configs) {
                lastConsidered = config;
                if (config.isAccept()) {
                    associatedConfigurations.add(config);
                    return 0;
                } else {
                    next.addAll(simulator.stepConfiguration(config));
                }
            }
            configs = next.toArray(new Configuration[0]);
        }
        associatedConfigurations.add(lastConsidered);
        return 1;
    }
}
