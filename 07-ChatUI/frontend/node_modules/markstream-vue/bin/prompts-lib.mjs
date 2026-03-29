import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
export const packageRoot = path.resolve(__dirname, '..')
export const defaultPromptsRoot = path.join(packageRoot, 'prompts')

export async function listPromptFiles(promptsRoot = defaultPromptsRoot) {
  const dirents = await fs.readdir(promptsRoot, { withFileTypes: true })
  return dirents
    .filter(dirent => dirent.isFile() && dirent.name.endsWith('.md'))
    .map(dirent => dirent.name)
    .sort()
}

export async function listPromptNames(promptsRoot = defaultPromptsRoot) {
  return (await listPromptFiles(promptsRoot)).map(file => file.replace(/\.md$/i, ''))
}

export async function resolvePromptPath(name, promptsRoot = defaultPromptsRoot) {
  const normalized = String(name ?? '').trim()
  if (!normalized)
    throw new Error('Prompt name is required')

  const candidates = normalized.endsWith('.md')
    ? [normalized]
    : [`${normalized}.md`, normalized]

  for (const candidate of candidates) {
    const fullPath = path.join(promptsRoot, candidate)
    try {
      await fs.access(fullPath)
      return fullPath
    }
    catch {
      // continue
    }
  }

  const available = await listPromptNames(promptsRoot)
  throw new Error(`Unknown prompt: ${normalized}. Available prompts: ${available.join(', ')}`)
}

export async function readPrompt(name, promptsRoot = defaultPromptsRoot) {
  const fullPath = await resolvePromptPath(name, promptsRoot)
  const content = await fs.readFile(fullPath, 'utf8')
  return { fullPath, content }
}
