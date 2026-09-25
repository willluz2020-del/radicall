# Gerar o VMB Music sem Android Studio

Você pode gerar o APK usando apenas o site do GitHub.

## 1. Criar uma conta no GitHub
Acesse github.com e crie sua conta, se ainda não tiver.

## 2. Criar um repositório
No GitHub, clique em **New repository**.

Nome sugerido: `VMBMusic`

Pode deixar como **Private** se não quiser tornar o código público.

Clique em **Create repository**.

## 3. Enviar os arquivos
Na página do repositório, escolha **uploading an existing file** / **Add file > Upload files**.

Envie **o conteúdo da pasta VMBMusicAndroid**, e não a pasta ZIP fechada.

É importante que estes itens apareçam na raiz do repositório:

- `.github`
- `app`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`

Depois clique em **Commit changes**.

## 4. Gerar o APK
Abra a aba **Actions** do repositório.

Clique em **Gerar APK VMB Music**.

Clique em **Run workflow** e depois em **Run workflow** novamente.

Aguarde o processo ficar com o símbolo verde de concluído.

## 5. Baixar o APK
Abra a execução concluída.

No final da página haverá a seção **Artifacts**.

Clique em **VMB-Music-APK**.

O GitHub baixa um ZIP. Dentro dele estará:

`VMB-Music.apk`

## 6. Instalar no celular
Envie `VMB-Music.apk` para o seu Android, toque no arquivo e autorize **Instalar apps desconhecidos** se o Android solicitar.

Depois toque em **Instalar**.

> Observação: este é um APK de teste (debug), adequado para instalar e testar no seu próprio celular. Para publicar na Play Store, deve ser gerado um AAB de release assinado.
