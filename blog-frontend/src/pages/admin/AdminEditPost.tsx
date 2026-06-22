import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { adminRequest } from "../../api/adminApi";
import { uploadImageToCloudinary } from "../../api/cloudinaryApi";
import { RichTextEditor } from "../../components/RichTextEditor";
import {PostPreviewModal} from "../../components/PostPreviewModal";

interface Category {
    id: number;
    name: string;
    slug: string;
}

interface Post {
    id: number;
    title: string;
    summary: string;
    content: string;
    coverImageUrl: string;
    metaTitle: string;
    metaDescription: string;
    status: "DRAFT" | "PUBLISHED";
    categories: Category[];
}

interface PagedResponse<T> {
    content: T[];
}

interface UpdatePostPayload {
    title: string;
    summary: string;
    content: string;
    coverImageUrl: string;
    metaTitle: string;
    metaDescription: string;
    categoryIds: number[];
}

export default function AdminEditPost() {
    const { id } = useParams();
    const navigate = useNavigate();

    const [categories, setCategories] = useState<Category[]>([]);
    const [categoryIds, setCategoryIds] = useState<number[]>([]);

    const [title, setTitle] = useState("");
    const [summary, setSummary] = useState("");
    const [content, setContent] = useState("");
    const [metaTitle, setMetaTitle] = useState("");
    const [metaDescription, setMetaDescription] = useState("");

    const [imageFile, setImageFile] = useState<File | null>(null);
    const [coverImageUrl, setCoverImageUrl] = useState("");
    const [previewUrl, setPreviewUrl] = useState("");

    const [loading, setLoading] = useState(true);
    const [uploadingImage, setUploadingImage] = useState(false);
    const [savingPost, setSavingPost] = useState(false);
    const [error, setError] = useState("");

    const [previewOpen, setPreviewOpen] = useState(false);

    const selectedCategories = categories.filter((category) =>
        categoryIds.includes(category.id)
    );

    const canSubmit = useMemo(() => {
        return (
            title.trim().length > 0 &&
            summary.trim().length > 0 &&
            content.trim().length > 0 &&
            categoryIds.length > 0 &&
            !uploadingImage &&
            !savingPost
        );
    }, [title, summary, content, categoryIds, uploadingImage, savingPost]);

    useEffect(() => {
        async function loadData() {
            try {
                setLoading(true);

                const [postData, categoriesData] = await Promise.all([
                    adminRequest<Post>(`/admin/posts/${id}`),
                    adminRequest<PagedResponse<Category> | Category[]>("/admin/categories"),
                ]);

                setTitle(postData.title ?? "");
                setSummary(postData.summary ?? "");
                setContent(postData.content ?? "");
                setCoverImageUrl(postData.coverImageUrl ?? "");
                setMetaTitle(postData.metaTitle ?? "");
                setMetaDescription(postData.metaDescription ?? "");
                setCategoryIds(postData.categories?.map((category) => category.id) ?? []);

                if (Array.isArray(categoriesData)) {
                    setCategories(categoriesData);
                } else {
                    setCategories(categoriesData.content ?? []);
                }
            } catch (error) {
                console.error("Erro ao carregar post:", error);
                setError("Não foi possível carregar o post.");
            } finally {
                setLoading(false);
            }
        }

        loadData();
    }, [id]);

    function toggleCategory(categoryId: number) {
        setCategoryIds((previous) => {
            if (previous.includes(categoryId)) {
                return previous.filter((id) => id !== categoryId);
            }

            return [...previous, categoryId];
        });
    }

    function handleImageChange(event: React.ChangeEvent<HTMLInputElement>) {
        const file = event.target.files?.[0];

        if (!file) return;

        if (!file.type.startsWith("image/")) {
            setError("Selecione apenas arquivos de imagem.");
            return;
        }

        if (file.size > 3 * 1024 * 1024) {
            setError("A imagem deve ter no máximo 3MB.");
            return;
        }

        setError("");
        setImageFile(file);
        setPreviewUrl(URL.createObjectURL(file));
    }

    async function handleUploadImage() {
        if (!imageFile) {
            setError("Selecione uma imagem antes de enviar.");
            return;
        }

        try {
            setUploadingImage(true);
            setError("");

            const uploadedUrl = await uploadImageToCloudinary(imageFile);

            setCoverImageUrl(uploadedUrl);
            setPreviewUrl("");
        } catch (error) {
            console.error("Erro no upload da imagem:", error);
            setError("Não foi possível enviar a imagem para o Cloudinary.");
        } finally {
            setUploadingImage(false);
        }
    }

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (!canSubmit) {
            setError("Preencha título, resumo, conteúdo e selecione pelo menos uma categoria.");
            return;
        }

        try {
            setSavingPost(true);
            setError("");

            let finalCoverImageUrl = coverImageUrl;

            if (imageFile && !coverImageUrl) {
                finalCoverImageUrl = await uploadImageToCloudinary(imageFile);
                setCoverImageUrl(finalCoverImageUrl);
            }

            const payload: UpdatePostPayload = {
                title: title.trim(),
                summary: summary.trim(),
                content: content.trim(),
                coverImageUrl: finalCoverImageUrl,
                metaTitle: metaTitle.trim() || title.trim(),
                metaDescription: metaDescription.trim() || summary.trim(),
                categoryIds,
            };

            await adminRequest(`/admin/posts/${id}`, {
                method: "PUT",
                body: JSON.stringify(payload),
            });

            navigate("/painel-secreto/posts");
        } catch (error) {
            console.error("Erro ao atualizar post:", error);
            setError("Não foi possível atualizar o post.");
        } finally {
            setSavingPost(false);
        }
    }

    if (loading) return <p>Carregando post...</p>;

    return (
        <div>
            <div className="admin-header">
                <div>
                    <h1>Editar post</h1>
                    <p>Atualize conteúdo, imagem, SEO e categorias do artigo.</p>
                </div>

                <div className="editor-actions">
                    <button
                        type="button"
                        className="secondary-admin-button"
                        onClick={() => setPreviewOpen(true)}
                    >
                        Preview
                    </button>

                    <Link to="/painel-secreto/posts">
                        <button type="button" className="secondary-admin-button">Voltar</button>
                    </Link>
                </div>
            </div>

            {error && <div className="admin-error">{error}</div>}

            <form className="post-editor-layout" onSubmit={handleSubmit}>
                <section className="post-editor-main">
                    <div className="admin-form-section">
                        <label>Título do post</label>
                        <input
                            value={title}
                            onChange={(event) => setTitle(event.target.value)}
                            maxLength={180}
                        />
                        <small>{title.length}/180 caracteres</small>
                    </div>

                    <div className="admin-form-section">
                        <label>Resumo</label>
                        <textarea
                            value={summary}
                            onChange={(event) => setSummary(event.target.value)}
                            maxLength={300}
                        />
                        <small>{summary.length}/300 caracteres</small>
                    </div>

                    <div className="admin-form-section">
                        <label>Conteúdo</label>
                        <RichTextEditor value={content} onChange={setContent} />
                    </div>

                    <div className="admin-form-section">
                        <label>SEO</label>

                        <input
                            placeholder="Meta title"
                            value={metaTitle}
                            onChange={(event) => setMetaTitle(event.target.value)}
                            maxLength={255}
                        />

                        <textarea
                            placeholder="Meta description"
                            value={metaDescription}
                            onChange={(event) => setMetaDescription(event.target.value)}
                            maxLength={300}
                        />

                        <small>Se vazio, o sistema usará o título e o resumo como base.</small>
                    </div>
                </section>

                <aside className="post-editor-sidebar">
                    <div className="admin-form-section">
                        <label>Imagem de capa</label>

                        <div className="image-upload-box">
                            {previewUrl || coverImageUrl ? (
                                <img src={previewUrl || coverImageUrl} alt="Prévia da capa" />
                            ) : (
                                <div>
                                    <strong>Prévia da imagem</strong>
                                    <span>JPG, PNG ou WebP até 3MB</span>
                                </div>
                            )}
                        </div>

                        <input type="file" accept="image/*" onChange={handleImageChange} />

                        <button
                            type="button"
                            className="secondary-admin-button full"
                            onClick={handleUploadImage}
                            disabled={!imageFile || uploadingImage}
                        >
                            {uploadingImage ? "Enviando..." : "Trocar imagem"}
                        </button>

                        {coverImageUrl && (
                            <small className="success-message">Imagem pronta para salvar.</small>
                        )}
                    </div>

                    <div className="admin-form-section">
                        <label>Categorias</label>

                        <div className="category-check-list">
                            {categories.map((category) => (
                                <label key={category.id}>
                                    <input
                                        type="checkbox"
                                        checked={categoryIds.includes(category.id)}
                                        onChange={() => toggleCategory(category.id)}
                                    />
                                    <span>{category.name}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    <div className="publish-box">
                        <strong>Salvar alterações</strong>
                        <p>As alterações serão aplicadas ao post. Se ele já estiver publicado, o conteúdo público será atualizado.</p>

                        <button disabled={!canSubmit}>
                            {savingPost ? "Salvando..." : "Salvar alterações"}
                        </button>
                    </div>
                </aside>
            </form>

            <PostPreviewModal
                isOpen={previewOpen}
                onClose={() => setPreviewOpen(false)}
                title={title}
                summary={summary}
                content={content}
                coverImageUrl={previewUrl || coverImageUrl}
                categories={selectedCategories}
            />
        </div>
    );
}