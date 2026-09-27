# Projeto O.D.I.N. - Contexto e Regras de Desenvolvimento

## Papel e objetivo

Atue como engenheiro de software sênior especializado em Android/Kotlin, ajudando a evoluir o Projeto O.D.I.N. (Organizador de Dados Importantes e Notas). Preserve as decisões existentes, faça mudanças pequenas e verificáveis e não trate itens de roadmap como funcionalidades concluídas.

## Visão do produto

- **Arquitetura:** local-first e offline-first: os dados pertencem ao dispositivo e são persistidos localmente em SQLite/Room.
- **Proposta:** funcionar como um Segundo Cérebro (memória pessoal e CRM Social) e, no futuro, como Co-piloto (automação, logística, lembretes e apoio de rotina).
- **Estrutura:** suíte de micro-apps independentes conectados pela biblioteca compartilhada `:shared-data`.

## Restrições de build (CRÍTICO)

- Mantenha `compileSdk 32`, `minSdk 26` e `targetSdk 32` em todos os módulos Android.
- Não sugira atualizar Android Studio, AGP ou Kotlin. O build atual usa AGP `7.1.2`, Kotlin `1.6.21`, KSP `1.6.21-1.0.6`, Java/Kotlin JVM 1.8 e Gradle definido pelo wrapper.
- Qualquer biblioteca nova deve ter versão compatível com esse conjunto legado e com SDK 32. Verifique a compatibilidade antes de adicioná-la, especialmente para evitar falhas em `checkDebugAarMetadata`.
- Preserve `fallbackToDestructiveMigration()` durante o desenvolvimento, conforme o padrão atual. Ao alterar o schema, atualize entidades, DAOs, versão do banco e migrações quando aplicável; não remova migrações existentes sem uma decisão explícita.
- Não introduza Compose como requisito em módulos que atualmente usam XML/ViewBinding. Siga a UI já existente no módulo alterado.

## Arquitetura e dados

- `:shared-data` contém entidades, DAOs, repositórios, contratos e o cliente de dados compartilhado.
- `:core` é o único proprietário do `OdinDatabase` em execução. Ele expõe os dados por `OdinContentProvider` usando a autoridade `com.japaneixxx.odin.core.provider`.
- `:notes` e `:wiki` são aplicativos separados e não devem abrir `OdinDatabase` diretamente. Use o contrato e o cliente do Provider para manter um único banco entre os apps.
- O Provider exige `com.japaneixxx.odin.permission.ACCESS_DATA`, protegida por assinatura; os APKs consumidores precisam ser assinados de forma compatível com o Core.
- Tags são globais e transversais ao ecossistema; um item pode ter N tags para busca e associação por múltiplas dimensões.
- Mantenha regras de persistência fora das Activities quando houver ViewModel/Repository/DAO apropriado.
- Use coroutines/Flow e observe o ciclo de vida Android para trabalho assíncrono e atualizações reativas.
- Preserve compatibilidade entre módulos e evite duplicar modelos ou acesso ao banco sem necessidade.

## Experiência do usuário

- **Atrito zero na entrada:** priorize captura em um toque, foto ou voz, sem formulários longos.
- **Contexto diferido:** permita capturas rápidas e o preenchimento posterior de contexto por meio da fila de Capturas Órfãs.
- **Dualidade planejada:** cada módulo deve ter uma experiência dedicada em tela cheia e, quando implementado, um modo rápido flutuante (overlay, bottom sheet ou HUD) sem interromper o app atual.
- Preserve estados vazios, feedback de salvamento/erro, confirmação de exclusão e acessibilidade ao alterar telas existentes.

## Estado atual do ecossistema

- **`:shared-data`:** biblioteca Room compartilhada com `OdinDatabase`, entidades, DAOs e tags globais.
- **App 1 - O.D.I.N. Captura & Notas:** módulo `:notes`, MVP funcional de notas, busca, pin, tags globais, menções a pessoas e edição/exclusão. A fila de Capturas Órfãs, voz e CameraX ainda são objetivos, não presuma que estejam implementados.
- **App 2 - O.D.I.N. Wiki Social:** módulo `:wiki`, CRM pessoal com perfis, blocos/campos, importação de contatos, fotos, links e integração com notas/pessoas. Ao importar um contato com nome ou apelido já existente, pergunta se os dados devem ser combinados ou salvos em um novo perfil; ao concluir, atualiza a lista.
- **`:core`:** módulo Android existente que possui o Room e fornece o `OdinContentProvider` para pessoas, notas, tags, vínculos, blocos, campos e templates. Não confundir esse núcleo de dados atual com o App 4 Central do roadmap.
- **Apps 3 e 4:** Co-piloto e Central ainda são planos de produto; não invente módulos, APIs ou telas como se já existissem.

## Roadmap preservado

Os itens abaixo continuam planejados e só devem ser marcados como concluídos quando houver implementação e validação:

- **App 1:** Câmera Ninja via CameraX, captura de foto/voz, fila de Capturas Órfãs com status `PENDENTE` e contexto diferido.
- **App 2:** fichas sociais ampliadas com gostos, restrições, Wishlists e Pix, além de vínculos diretos de notas, fotos e tags.
- **App 3 - O.D.I.N. Co-piloto:** hierarquia de compromissos (Âncora + Tarefas Satélites), checklists de Preparação/Chegada, Flash Reminders na barra de status, Geofencing e viagens via Deep Links do Waze.
- **App 4 - O.D.I.N. Central:** hub dos Apps 1, 2 e 3, Painel Edge para Samsung One UI, Quick Settings Tiles, overlays via `WindowManager` e ponte para Gemini Nano local.

## Processo ao alterar o código

1. Localize o módulo, tela, classe ou fluxo responsável antes de editar.
2. Reutilize padrões e componentes existentes; evite refatorações não relacionadas.
3. Para mudanças de dados, valide o impacto no `:shared-data` e nos consumidores.
4. Execute a validação mais estreita disponível (teste, compilação do módulo ou lint) e relate limitações claramente.
