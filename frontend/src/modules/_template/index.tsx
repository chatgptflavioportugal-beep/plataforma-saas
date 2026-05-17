// Template para novos módulos.
// Copie esta pasta e renomeie para o nome do módulo.
// Estrutura sugerida:
//   modules/<nome>/
//     index.tsx          ← página principal (este arquivo)
//     hooks/             ← hooks específicos do módulo
//     components/        ← componentes internos do módulo
//     services/          ← chamadas de API do módulo (opcional)

export function TemplatePage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-900">Módulo</h1>
      <p className="text-gray-500">Substitua este conteúdo pela implementação do módulo.</p>
    </div>
  )
}
