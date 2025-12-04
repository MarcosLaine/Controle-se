
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Sistema de Gerenciamento Financeiro Controle-se
 * Interface principal que integra todas as funcionalidades do sistema
 */
public class SistemaFinanceiro {
    private BancoDados bancoDados;
    private Usuario usuarioLogado;
    private Scanner scanner;
    
    public SistemaFinanceiro() {
        this.bancoDados = new BancoDados();
        this.usuarioLogado = null;
        this.scanner = new Scanner(System.in);
    }
    
    public void executar() {
        System.out.println("=== SISTEMA CONTROLE-SE ===");
        System.out.println("Sistema de Controle Financeiro Pessoal");
        System.out.println("Implementação com Estruturas de Dados Avançadas");
        
        int opcao;
        do {
            mostrarMenuPrincipal();
            opcao = lerInteiro("Escolha uma opção: ");
            
            switch (opcao) {
                case 1:
                    autenticarUsuario();
                    break;
                case 2:
                    cadastrarUsuario();
                    break;
                case 3:
                    if (usuarioLogado != null) {
                        menuUsuarioLogado();
                    } else {
                        System.out.println("Você precisa estar logado para acessar esta funcionalidade!");
                    }
                    break;
                case 4:
                    bancoDados.imprimirEstatisticas();
                    break;
                case 5:
                    bancoDados.imprimirEstruturasIndices();
                    break;
                case 0:
                    System.out.println("Saindo do sistema...");
                    break;
                default:
                    System.out.println("Opção inválida!");
            }
        } while (opcao != 0);
        
        scanner.close();
    }
    
    private void mostrarMenuPrincipal() {
        System.out.println("\n=== MENU PRINCIPAL ===");
        if (usuarioLogado != null) {
            System.out.println("Usuário logado: " + usuarioLogado.getNome());
        }
        System.out.println("1. Fazer Login");
        System.out.println("2. Cadastrar Usuário");
        System.out.println("3. Menu do Usuário");
        System.out.println("4. Estatísticas do Sistema");
        System.out.println("5. Visualizar Estruturas de Índices");
        System.out.println("0. Sair");
    }
    
    private void autenticarUsuario() {
        System.out.println("\n=== LOGIN ===");
        String email = lerString("Email: ");
        String senha = lerString("Senha: ");
        
        if (bancoDados.autenticarUsuario(email, senha)) {
            usuarioLogado = bancoDados.buscarUsuarioPorEmail(email);
            System.out.println("Login realizado com sucesso!");
        } else {
            System.out.println("Email ou senha incorretos!");
        }
    }
    
    private void cadastrarUsuario() {
        System.out.println("\n=== CADASTRO DE USUÁRIO ===");
        String nome = lerString("Nome: ");
        String email = lerString("Email: ");
        String senha = lerString("Senha: ");
        
        try {
            int idUsuario = bancoDados.cadastrarUsuario(nome, email, senha);
            System.out.println("Usuário cadastrado com sucesso! ID: " + idUsuario);
        } catch (RuntimeException e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }
    
    private void menuUsuarioLogado() {
        int opcao;
        do {
            System.out.println("\n=== MENU DO USUÁRIO ===");
            System.out.println("1. Gerenciar Categorias");
            System.out.println("2. Gerenciar Contas");
            System.out.println("3. Registrar Gasto");
            System.out.println("4. Registrar Receita");
            System.out.println("5. Definir Orçamento");
            System.out.println("6. Visualizar Relatórios");
            System.out.println("7. Dashboard Financeiro");
            System.out.println("8. Filtrar/Listar Gastos");
            System.out.println("9. Pesquisar por padrão (KMP / BM)"); 
            System.out.println("0. Voltar");
            
            opcao = lerInteiro("Escolha uma opção: ");
            
            switch (opcao) {
                case 1:
                    menuCategorias();
                    break;
                case 2:
                    menuContas();
                    break;
                case 3:
                    registrarGasto();
                    break;
                case 4:
                    registrarReceita();
                    break;
                case 5:
                    definirOrcamento();
                    break;
                case 6:
                    visualizarRelatorios();
                    break;
                case 7:
                    visualizarDashboard();
                    break;
                case 8:
                    filtrarGastos();
                case 9:
                    menuPesquisarPadrao();
                    break;
                case 0:
                    break;
                default:
                    System.out.println("Opção inválida!");
            }
        } while (opcao != 0);
    }
    
    private void menuCategorias() {
        int opcao;
        do {
            System.out.println("\n=== GERENCIAR CATEGORIAS ===");
            System.out.println("1. Listar Categorias");
            System.out.println("2. Cadastrar Categoria");
            System.out.println("0. Voltar");
            
            opcao = lerInteiro("Escolha uma opção: ");
            
            switch (opcao) {
                case 1:
                    listarCategorias();
                    break;
                case 2:
                    cadastrarCategoria();
                    break;
                case 0:
                    break;
                default:
                    System.out.println("Opção inválida!");
            }
        } while (opcao != 0);
    }
    
    private void listarCategorias() {
        List<Categoria> categorias = bancoDados.buscarCategoriasPorUsuario(usuarioLogado.getIdUsuario());
        System.out.println("\n=== SUAS CATEGORIAS ===");
        if (categorias.isEmpty()) {
            System.out.println("Nenhuma categoria cadastrada.");
        } else {
            for (Categoria categoria : categorias) {
                System.out.println(categoria);
            }
        }
    }
    
    private void cadastrarCategoria() {
        String nome = lerString("Nome da categoria: ");
        int idCategoria = bancoDados.cadastrarCategoria(nome, usuarioLogado.getIdUsuario());
        System.out.println("Categoria cadastrada com sucesso! ID: " + idCategoria);
    }
    
    private void menuContas() {
        int opcao;
        do {
            System.out.println("\n=== GERENCIAR CONTAS ===");
            System.out.println("1. Listar Contas");
            System.out.println("2. Cadastrar Conta");
            System.out.println("3. Filtrar por Tipo");
            System.out.println("0. Voltar");
            
            opcao = lerInteiro("Escolha uma opção: ");
            
            switch (opcao) {
                case 1:
                    listarContas();
                    break;
                case 2:
                    cadastrarConta();
                    break;
                case 3:
                    filtrarContasPorTipo();
                    break;
                case 0:
                    break;
                default:
                    System.out.println("Opção inválida!");
            }
        } while (opcao != 0);
    }
    
    private void listarContas() {
        List<Conta> contas = bancoDados.buscarContasPorUsuario(usuarioLogado.getIdUsuario());
        System.out.println("\n=== SUAS CONTAS ===");
        if (contas.isEmpty()) {
            System.out.println("Nenhuma conta cadastrada.");
        } else {
            for (Conta conta : contas) {
                System.out.println(conta);
            }
        }
    }
    
    private void cadastrarConta() {
        String nome = lerString("Nome da conta: ");
        String tipo = lerString("Tipo da conta (ex: Corrente, Poupança, Cartão): ");
        double saldo = lerDouble("Saldo atual: ");
        
        int idConta = bancoDados.cadastrarConta(nome, tipo, saldo, usuarioLogado.getIdUsuario());
        System.out.println("Conta cadastrada com sucesso! ID: " + idConta);
    }
    
    private void filtrarContasPorTipo() {
        String tipo = lerString("Digite o tipo para filtrar: ");
        List<Conta> contas = bancoDados.buscarContasPorTipo(tipo);
        
        System.out.println("\n=== CONTAS DO TIPO: " + tipo.toUpperCase() + " ===");
        if (contas.isEmpty()) {
            System.out.println("Nenhuma conta encontrada para este tipo.");
        } else {
            for (Conta conta : contas) {
                System.out.println(conta);
            }
        }
    }
    
    private void registrarGasto() {
        System.out.println("\n=== REGISTRAR GASTO ===");
        
        // Lista categorias disponíveis
        List<Categoria> categorias = bancoDados.buscarCategoriasPorUsuario(usuarioLogado.getIdUsuario());
        if (categorias.isEmpty()) {
            System.out.println("Você precisa ter pelo menos uma categoria cadastrada!");
            return;
        }
        
        System.out.println("Categorias disponíveis:");
        for (int i = 0; i < categorias.size(); i++) {
            System.out.println((i + 1) + ". " + categorias.get(i).getNome());
        }
        
        int escolha = lerInteiro("Escolha uma categoria (número): ") - 1;
        if (escolha < 0 || escolha >= categorias.size()) {
            System.out.println("Escolha inválida!");
            return;
        }
        
        Categoria categoriaEscolhida = categorias.get(escolha);
        String descricao = lerString("Descrição: ");
        double valor = lerDouble("Valor: ");
        LocalDate data = lerData("Data (dd/mm/aaaa): ");
        String frequencia = lerString("Frequência (ex: Único, Mensal, Semanal): ");
        
        int idGasto = bancoDados.cadastrarGasto(descricao, valor, data, frequencia, usuarioLogado.getIdUsuario(), categoriaEscolhida.getIdCategoria(), 1);
        System.out.println("Gasto registrado com sucesso! ID: " + idGasto);
    }
    
    private void registrarReceita() {
        System.out.println("\n=== REGISTRAR RECEITA ===");
        System.out.print("Descrição: ");
        String descricao = scanner.nextLine();
        double valor = lerDouble("Valor: ");
        LocalDate data = lerData("Data (dd/mm/aaaa): ");
        
        int idReceita = bancoDados.cadastrarReceita(descricao, valor, data, usuarioLogado.getIdUsuario(), 1);
        System.out.println("Receita registrada com sucesso! ID: " + idReceita);
    }
    
    private void definirOrcamento() {
        System.out.println("\n=== DEFINIR ORÇAMENTO ===");
        
        // Lista categorias disponíveis
        List<Categoria> categorias = bancoDados.buscarCategoriasPorUsuario(usuarioLogado.getIdUsuario());
        if (categorias.isEmpty()) {
            System.out.println("Você precisa ter pelo menos uma categoria cadastrada!");
            return;
        }
        
        System.out.println("Categorias disponíveis:");
        for (int i = 0; i < categorias.size(); i++) {
            System.out.println((i + 1) + ". " + categorias.get(i).getNome());
        }
        
        int escolha = lerInteiro("Escolha uma categoria (número): ") - 1;
        if (escolha < 0 || escolha >= categorias.size()) {
            System.out.println("Escolha inválida!");
            return;
        }
        
        Categoria categoriaEscolhida = categorias.get(escolha);
        double valorPlanejado = lerDouble("Valor planejado: ");
        String periodo = lerString("Período (ex: Mensal, Anual): ");
        
        int idOrcamento = bancoDados.cadastrarOrcamento(valorPlanejado, periodo, categoriaEscolhida.getIdCategoria(), usuarioLogado.getIdUsuario());
        System.out.println("Orçamento definido com sucesso! ID: " + idOrcamento);
    }
    
    private void visualizarRelatorios() {
        System.out.println("\n=== RELATÓRIOS FINANCEIROS ===");
        
        double totalGastos = bancoDados.calcularTotalGastosUsuario(usuarioLogado.getIdUsuario());
        double totalReceitas = bancoDados.calcularTotalReceitasUsuario(usuarioLogado.getIdUsuario());
        double saldo = bancoDados.calcularSaldoUsuario(usuarioLogado.getIdUsuario());
        double saldoContas = bancoDados.calcularTotalSaldoContasUsuario(usuarioLogado.getIdUsuario());
        
        System.out.println("Total de Gastos: R$ " + String.format("%.2f", totalGastos));
        System.out.println("Total de Receitas: R$ " + String.format("%.2f", totalReceitas));
        System.out.println("Saldo Líquido: R$ " + String.format("%.2f", saldo));
        System.out.println("Saldo Total das Contas: R$ " + String.format("%.2f", saldoContas));
        
        // Relatório por categoria
        System.out.println("\n=== GASTOS POR CATEGORIA ===");
        List<Categoria> categorias = bancoDados.buscarCategoriasPorUsuario(usuarioLogado.getIdUsuario());
        if (categorias.isEmpty()) {
            System.out.println("Nenhuma categoria cadastrada.");
        } else {
            for (Categoria categoria : categorias) {
                double totalCategoria = bancoDados.calcularTotalGastosPorCategoria(categoria.getIdCategoria());
                System.out.println(categoria.getNome() + ": R$ " + String.format("%.2f", totalCategoria));
            }
        }
    }
    
    private void visualizarDashboard() {
        System.out.println("\n=== DASHBOARD FINANCEIRO ===");
        
        // Resumo geral
        visualizarRelatorios();
        
        // Últimos gastos
        List<Gasto> gastos = bancoDados.buscarGastosPorUsuario(usuarioLogado.getIdUsuario());
        System.out.println("\nÚltimos Gastos:");
        if (gastos.isEmpty()) {
            System.out.println("Nenhum gasto registrado.");
        } else {
            gastos.stream().limit(5).forEach(System.out::println);
        }
        
        // Últimas receitas
        List<Receita> receitas = bancoDados.buscarReceitasPorUsuario(usuarioLogado.getIdUsuario());
        System.out.println("\nÚltimas Receitas:");
        if (receitas.isEmpty()) {
            System.out.println("Nenhuma receita registrada.");
        } else {
            receitas.stream().limit(5).forEach(System.out::println);
        }
    }
    
    private void filtrarGastos() {
        System.out.println("\n=== FILTRAR/LISTAR GASTOS ===");
        System.out.println("1. Listar todos os gastos");
        System.out.println("2. Filtrar por data");
        System.out.println("3. Filtrar por categoria");
        System.out.println("0. Voltar");
        
        int opcao = lerInteiro("Escolha uma opção: ");
        
        switch (opcao) {
            case 1:
                listarTodosGastos();
                break;
            case 2:
                filtrarGastosPorData();
                break;
            case 3:
                filtrarGastosPorCategoria();
                break;
            case 0:
                break;
            default:
                System.out.println("Opção inválida!");
        }
    }
    
    private void listarTodosGastos() {
        List<Gasto> gastos = bancoDados.buscarGastosPorUsuario(usuarioLogado.getIdUsuario());
        System.out.println("\n=== TODOS OS GASTOS ===");
        if (gastos.isEmpty()) {
            System.out.println("Nenhum gasto registrado.");
        } else {
            gastos.forEach(System.out::println);
        }
    }
    
    private void filtrarGastosPorCategoria() {
        // Lista categorias disponíveis
        List<Categoria> categorias = bancoDados.buscarCategoriasPorUsuario(usuarioLogado.getIdUsuario());
        if (categorias.isEmpty()) {
            System.out.println("Nenhuma categoria cadastrada.");
            return;
        }
        
        System.out.println("Categorias disponíveis:");
        for (int i = 0; i < categorias.size(); i++) {
            System.out.println((i + 1) + ". " + categorias.get(i).getNome());
        }
        
        int escolha = lerInteiro("Escolha uma categoria para filtrar (número): ") - 1;
        if (escolha < 0 || escolha >= categorias.size()) {
            System.out.println("Escolha inválida!");
            return;
        }
        
        Categoria categoriaEscolhida = categorias.get(escolha);
        List<Gasto> gastos = bancoDados.buscarGastosPorCategoria(categoriaEscolhida.getIdCategoria());
        
        System.out.println("\n=== GASTOS DA CATEGORIA: " + categoriaEscolhida.getNome().toUpperCase() + " ===");
        if (gastos.isEmpty()) {
            System.out.println("Nenhum gasto encontrado para esta categoria.");
        } else {
            double total = 0;
            for (Gasto gasto : gastos) {
                System.out.println(gasto);
                total += gasto.getValor();
            }
            System.out.println("\nTotal gasto nesta categoria: R$ " + String.format("%.2f", total));
        }
    }
    
    private void filtrarGastosPorData() {
        LocalDate data = lerData("Digite a data para filtrar (dd/mm/aaaa): ");
        List<Gasto> gastos = bancoDados.buscarGastosPorData(data);
        
        System.out.println("\n=== GASTOS DO DIA " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " ===");
        if (gastos.isEmpty()) {
            System.out.println("Nenhum gasto encontrado para esta data.");
        } else {
            gastos.forEach(System.out::println);
        }

    }

    private void menuPesquisarPadrao() {
        System.out.println("\n=== PESQUISA POR PADRÃO DE TEXTO ===");
        System.out.println("Busca realizada nas descrições de Gastos e Receitas.");
        System.out.println("1. Algoritmo KMP (Knuth-Morris-Pratt)");
        System.out.println("2. Algoritmo Boyer-Moore");
        
        int algoritmo = lerInteiro("Escolha o algoritmo: ");
        if (algoritmo != 1 && algoritmo != 2) {
            System.out.println("Opção inválida!");
            return;
        }
        
        String termo = lerString("Digite o termo a pesquisar: ");
        if (termo == null || termo.trim().isEmpty()) {
            System.out.println("Padrão vazio.");
            return;
        }
        
        long inicio = System.nanoTime();
        int encontrados = 0;
        
        // Busca em Gastos
        System.out.println("\n--- Resultados em Gastos ---");
        List<Gasto> gastos = bancoDados.buscarGastosPorUsuario(usuarioLogado.getIdUsuario());
        for (Gasto g : gastos) {
            // Busca case-insensitive (convertendo tudo para minúsculo)
            String texto = g.getDescricao().toLowerCase();
            String padrao = termo.toLowerCase();
            boolean match = false;
            
            if (algoritmo == 1) {
                match = PatternMatching.searchKMP(texto, padrao);
            } else {
                match = PatternMatching.searchBoyerMoore(texto, padrao);
            }
            
            if (match) {
                System.out.println(g);
                encontrados++;
            }
        }
        
        // Busca em Receitas
        System.out.println("\n--- Resultados em Receitas ---");
        List<Receita> receitas = bancoDados.buscarReceitasPorUsuario(usuarioLogado.getIdUsuario());
        for (Receita r : receitas) {
            String texto = r.getDescricao().toLowerCase();
            String padrao = termo.toLowerCase();
            boolean match = false;
            
            if (algoritmo == 1) {
                match = PatternMatching.searchKMP(texto, padrao);
            } else {
                match = PatternMatching.searchBoyerMoore(texto, padrao);
            }
            
            if (match) {
                System.out.println(r);
                encontrados++;
            }
        }
        
        long fim = System.nanoTime();
        System.out.println("\n==================================");
        System.out.println("Total de registros encontrados: " + encontrados);
        System.out.println("Algoritmo utilizado: " + (algoritmo == 1 ? "KMP" : "Boyer-Moore"));
        System.out.println("Tempo de execução: " + (fim - inicio) + " nanosegundos");
        System.out.println("==================================");
        
        pausar();
    }
    
    // Métodos auxiliares
     private int lerInteiro(String mensagem) {
        System.out.print(mensagem);
        int valor = scanner.nextInt();
        scanner.nextLine(); // Consome o \n restante imediatamente
        return valor;
    }
    
    private String lerString(String mensagem) {
        System.out.print(mensagem);
        return scanner.nextLine();
    }
    
  private double lerDouble(String mensagem) {
        System.out.print(mensagem);
        double valor = scanner.nextDouble();
        scanner.nextLine(); // Consome o \n restante imediatamente
        return valor;
    }
  
    private LocalDate lerData(String mensagem) {
        System.out.print(mensagem);
        // scanner.nextLine(); // REMOVER ESTA LINHA
        String dataStr = scanner.nextLine();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return LocalDate.parse(dataStr, formatter);
    }

    private void pausar() {
        System.out.println("\nPressione Enter para continuar...");
        scanner.nextLine();
    }
}
