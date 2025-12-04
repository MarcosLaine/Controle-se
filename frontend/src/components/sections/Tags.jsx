import React, { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Tag } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';
import SkeletonSection from '../common/SkeletonSection';

export default function Tags() {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [tags, setTags] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingTag, setEditingTag] = useState(null);
  const [formData, setFormData] = useState({ nome: '', cor: '#3498db' });

  useEffect(() => {
    if (user) loadTags();
  }, [user]);

  const loadTags = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/tags?userId=${user.id}`);
      if (response.success) setTags(response.data || []);
    } catch (error) {
      toast.error(t('tags.errorLoading'));
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const data = { nome: formData.nome, cor: formData.cor, userId: user.id };
      if (editingTag) {
        const response = await api.put(`/tags/${editingTag.idTag}`, { ...data, id: editingTag.idTag });
        if (response.success) {
          toast.success(t('tags.updatedSuccess'));
          loadTags();
          handleCloseModal();
        }
      } else {
        const response = await api.post('/tags', data);
        if (response.success) {
          toast.success(t('tags.createdSuccess'));
          loadTags();
          handleCloseModal();
        }
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar tag');
    }
  };

  const handleDelete = async (id) => {
    if (!confirm(t('categories.tagDeleteConfirm'))) return;
    try {
      const response = await api.delete(`/tags/${id}`);
      if (response.success) {
        toast.success(t('tags.deletedSuccess'));
        loadTags();
      }
    } catch (error) {
      toast.error(t('tags.errorDeleting'));
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingTag(null);
    setFormData({ nome: '', cor: '#3498db' });
  };

  if (loading) {
    return <SkeletonSection type="tags" />;
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">Tags</h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">Organize suas transações com tags</p>
        </div>
        <button onClick={() => setShowModal(true)} className="btn-primary">
          <Plus className="w-4 h-4" />
          Nova Tag
        </button>
      </div>

      {tags.length === 0 ? (
        <div className="card text-center py-12">
          <Tag className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-600 dark:text-gray-400">Nenhuma tag criada</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {tags.map((tag) => (
            <div key={tag.idTag} className="card">
              <div className="flex items-center gap-3 mb-4">
                <span
                  className="px-4 py-2 rounded-lg text-white font-medium"
                  style={{ backgroundColor: tag.cor }}
                >
                  <Tag className="w-4 h-4 inline mr-2" />
                  {tag.nome}
                </span>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => {
                    setEditingTag(tag);
                    setFormData({ nome: tag.nome, cor: tag.cor });
                    setShowModal(true);
                  }}
                  className="btn-secondary flex-1"
                >
                  <Edit className="w-4 h-4" />
                  Editar
                </button>
                <button onClick={() => handleDelete(tag.idTag)} className="btn-danger flex-1">
                  <Trash2 className="w-4 h-4" />
                  Excluir
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingTag ? 'Editar Tag' : 'Nova Tag'}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Nome da Tag</label>
            <input
              type="text"
              value={formData.nome}
              onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
              className="input"
              placeholder="Ex: Urgente, Pessoal..."
              required
            />
          </div>
          <div>
            <label className="label">Cor</label>
            <div className="flex items-center gap-4">
              <input
                type="color"
                value={formData.cor}
                onChange={(e) => setFormData({ ...formData, cor: e.target.value })}
                className="w-20 h-12 rounded-lg cursor-pointer"
              />
              <div
                className="px-4 py-2 rounded-lg text-white font-medium"
                style={{ backgroundColor: formData.cor }}
              >
                Preview
              </div>
            </div>
          </div>
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={handleCloseModal} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" className="btn-primary">
              {editingTag ? 'Atualizar' : 'Criar'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

