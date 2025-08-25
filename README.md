# Discloud Plugin for IntelliJ IDEA

[![IntelliJ Plugin](https://img.shields.io/badge/IntelliJ-Plugin-blue.svg)](https://plugins.jetbrains.com/)  
[![Discloud](https://img.shields.io/badge/Powered%20by-Discloud-purple.svg)](https://discloud.com/)

Um simples **plugin para IntelliJ IDEA** que permite enviar seus projetos **.jar** diretamente para a [Discloud](https://discloud.com/), a melhor hospedagem Brasileira para projetos grandes e pequenos.  
Ideal para desenvolvedores Kotlin/Java que desejam agilizar o processo de deploy.

---

## ✨ Funcionalidades

- 🔑 Configuração da **API Key da Discloud** diretamente no IntelliJ (Settings → Discloud Settings)  
- 🚀 Clique com o botão direito em qualquer `.jar` no seu projeto e envie instantaneamente para a Discloud (**Commit Discloud**)  
- 🛠️ Integração com a [API oficial da Discloud](https://discloud.github.io/apidoc/)  
- 📦 Persistência da API Key nas configurações do IntelliJ  
- 📋 Feedback direto no IDE com status e resposta da API  
- 🖥️ **Tool Window** para gerenciar aplicativos: listar apps, status online/offline, iniciar/parar e visualizar logs  

---

## 📥 Instalação

Atualmente o plugin ainda não está publicado na JetBrains Marketplace, então para testar:

1. Clone este repositório:
   ```bash
   git clone https://github.com/0xviny/discloud-plugin.git
   cd discloud-plugin

2. Abra o projeto no **IntelliJ IDEA** (Community ou Ultimate)
3. Rode o plugin:

    * Vá em **Gradle Tool Window → Tasks → intellij → runIde**
    * O IntelliJ abrirá em modo sandbox com o plugin carregado

---

## 🔑 Configuração da API Key

Na **primeira inicialização** do plugin, será solicitado que você insira sua **API Key da Discloud**.
Você também pode alterar depois em:

`File → Settings → Tools → Discloud Settings`

> Sua chave é salva localmente em um arquivo de configuração do IntelliJ (`discloud.xml`) e usada em todos os commits seguintes.

---

## 🚀 Como usar

1. Compile ou gere seu `.jar` do projeto
2. No **Project Explorer**, clique com o botão direito no arquivo `.jar`
3. Clique em **Commit Discloud**
4. O plugin fará o upload para a Discloud e mostrará o resultado no IntelliJ

---

## 📝 Histórico de Versões

### 1.0.0 - Initial Version

* Adicionado **Tool Window** para gerenciar apps da Discloud (listar apps, status online/offline)
* Implementado botão **Start** e **Stop** para iniciar/parar aplicativos
* Implementado botão **Refresh** para atualizar a lista de apps
* Implementado botão **Logs** para visualizar os logs do terminal de cada app
* Integração completa com a **Discloud API** usando API Key configurável
* Status dos apps exibido diretamente no **Tool Window** do IntelliJ
* Persistência da **API Key** nas configurações do IntelliJ
* Ação de **Commit Discloud** para enviar `.jar` diretamente para a Discloud via clique direito
* Feedback direto no IDE com respostas da API (upload, start, stop, logs)

---

## 📌 Roadmap

* [x] Commit de arquivos `.jar` via API da Discloud
* [x] Persistência e configuração da API Key
* [x] Tool Window lateral para gerenciar aplicativos (listar apps, iniciar/parar/reiniciar, logs etc.)
* [ ] Suporte a commits automáticos após build
* [x] Publicação no JetBrains Marketplace

---

## 🤝 Contribuindo

Contribuições são bem-vindas!
Abra uma **Issue** ou envie um **Pull Request** com melhorias, correções ou novas funcionalidades.

---

## 📜 Licença

Este projeto é distribuído sob a licença **MIT**
Veja o arquivo [LICENSE](LICENSE) para mais informações

---

## 💙 Créditos

* [Discloud](https://discloud.com/) — hospedagem oficial
* [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html) — base para desenvolvimento de plugins