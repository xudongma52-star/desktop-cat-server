import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })

export function renderMarkdown(content: string): string {
  return DOMPurify.sanitize(markdown.render(content), { USE_PROFILES: { html: true } })
}
