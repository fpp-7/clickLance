import Link from "next/link";
import Image from "next/image";
import {
  Play,
  Search,
  BookmarkCheck,
  MousePointer2,
  Film,
  CircleCheck,
  MoveUpRight,
} from "lucide-react";
export default function PaginaInicial() {
  return (
    <div className="page-shell" style={{ paddingTop: 0 }}>
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <p className="hero-intro">
            <Film aria-hidden="true" />
            Seu futebol merece replay.
          </p>
          <h1 id="hero-title">
            O jogo acaba.
            <br />O lance fica.
          </h1>
          <p className="hero-description">
            Aquele gol, o passe improvável, a defesa do jogo. Aperte o botão na quadra e reviva o
            que vale a pena guardar.
          </p>
          <div className="hero-actions">
            <Link href="/arenas" className="button">
              <Search size={17} aria-hidden="true" />
              Encontrar minha quadra
            </Link>
            <a href="#como-funciona" className="text-link" style={{ fontSize: 13 }}>
              Como funciona
            </a>
          </div>
          <p className="hero-note">
            <CircleCheck size={15} aria-hidden="true" />
            Piloto gratuito. Seus melhores momentos, na sua biblioteca.
          </p>
        </div>
        <div className="hero-visual">
          <div className="hero-corner" aria-hidden="true" />
          <div className="hero-photo">
            <Image
              src="/images/society-hero-v1.png"
              alt="Jogador de society dominando a bola junto à linha lateral, sob a luz do fim de tarde"
              fill
              priority
              sizes="(max-width:720px) 94vw, 560px"
            />
            <div className="hero-image-shade" />
            <span className="hero-timestamp">
              <Film size={12} aria-hidden="true" />O próximo pode ser o seu.
            </span>
            <Link href="/arenas" className="hero-play" aria-label="Encontrar quadra e ver replays">
              <Play fill="currentColor" aria-hidden="true" />
            </Link>
            <div className="hero-caption">
              <p>Vale ver de novo.</p>
              <span>Imagem ilustrativa</span>
            </div>
          </div>
          <div className="saved-sticker">
            <BookmarkCheck aria-hidden="true" />
            <div>
              <b>Do jogo para a memória.</b>
              <small>Guarde seus favoritos em um só lugar.</small>
            </div>
          </div>
        </div>
      </section>
      <form className="finder" action="/arenas" role="search">
        <div>
          <h2>Onde foi o seu jogo?</h2>
          <p>Encontre a arena. Escolha a quadra. Reveja o lance.</p>
        </div>
        <label className="finder-input">
          <Search size={20} aria-hidden="true" />
          <span className="visually-hidden">Nome da arena</span>
          <input
            type="search"
            name="busca"
            placeholder="Digite o nome da arena"
            autoComplete="off"
          />
        </label>
        <button className="button" type="submit">
          Buscar arena
          <MoveUpRight size={16} aria-hidden="true" />
        </button>
      </form>
      <section className="how-section" id="como-funciona" aria-labelledby="how-heading">
        <div className="section-title-row">
          <h2 id="how-heading">Jogue. Clique. Reviva.</h2>
          <p>
            Do lado de fora da quadra,
            <br />o jogo continua com você.
          </p>
        </div>
        <div className="how-grid">
          <article className="how-item">
            <MousePointer2 aria-hidden="true" />
            <h3>Aconteceu? Aperte o botão.</h3>
            <p>
              O botão da quadra pede o replay dos momentos ao redor da jogada. Você continua no
              jogo.
            </p>
          </article>
          <article className="how-item">
            <Search aria-hidden="true" />
            <h3>Encontre pelo horário.</h3>
            <p>
              Entre na sua conta, escolha a quadra e o horário em que jogou. Seus lances ficam
              fáceis de encontrar.
            </p>
          </article>
          <article className="how-item">
            <BookmarkCheck aria-hidden="true" />
            <h3>Guarde o que foi seu.</h3>
            <p>
              Assista, baixe e salve até 100 lances na sua biblioteca, disponíveis enquanto a conta
              estiver ativa.
            </p>
          </article>
        </div>
      </section>
    </div>
  );
}
