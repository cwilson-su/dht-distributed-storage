\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{psar}[2026/02/08 PSAR academic report class]

\LoadClass[11pt,a4paper]{article}

% Move babel here with shorthands=off to prevent the clash globally
\RequirePackage[shorthands=off, UKenglish, french]{babel}

% -----sets British English conventions-----
\usepackage[UKenglish]{babel}      
%\usepackage[ddmmyyyy]{datetime}  

% ---- Fonts ----
\RequirePackage{tgheros}   % TeX Gyre Heros (Arial-like)
\renewcommand{\familydefault}{\sfdefault}

\RequirePackage[
  top=2cm,
  bottom=2cm,
  left=2cm,
  right=2cm
]{geometry}


% ---- Headers ----
\RequirePackage{fancyhdr}
\pagestyle{fancy}
\fancyhf{}
\fancyhead[C]{\thepage}
\fancyfoot[L]{PSAR 2026}
\fancyfoot[R]{Genevieve, Gunes, Boudalil}
\renewcommand{\headrulewidth}{0pt}

% ---- Paragraph style ----
\setlength{\parindent}{0pt}
\setlength{\parskip}{6pt}

% ---- Section formatting ----
\RequirePackage{titlesec}
\titleformat{\section}
  {\bfseries}
  {\thesection}
  {1em}
  {}

% ---- Metadata commands ----
\newcommand{\projecttitle}[1]{\def\@projecttitle{#1}}
\newcommand{\projectsubtitle}[1]{\def\@projectsubtitle{#1}}
\newcommand{\projectcode}[1]{\def\@projectcode{#1}}
\newcommand{\authors}[1]{\def\@authors{#1}}
\newcommand{\Instructor}[1]{\def\@Instructor{#1}}
\newcommand{\institution}[1]{\def\@institution{#1}}
\newcommand{\projectdate}[1]{\def\@projectdate{#1}}


% ---- Title page ----
\newcommand{\maketitlepage}{
  \thispagestyle{fancy}

  % ---- Title block ----
  {\Large\bfseries \@projecttitle \par}
   \vspace{0.2em}
  {\large \@projectsubtitle \par}

  \vspace{0.5em}

  {\@projectcode \quad -- \quad \@institution \par}
  \vspace{0.5em}

  {\textbf{Auteurs:} \@authors \par}
  {\textbf{Instructeur:} \@Instructor \par}
  {\textbf{Date:} \@projectdate \par}

  \vspace{1em}
}

\usepackage{tocloft}
\setlength{\cftbeforesecskip}{0pt}

\newcommand{\psarstartline}{
  \vspace{0.5em}
  \hrule
  \vspace{0.8em}
}

%--------- bibliography -------------
\usepackage[backend=biber, style=authoryear, maxcitenames=3]{biblatex}
\addbibresource{references.bib} 

% ----------- coloured boxes --------------
\usepackage[most]{tcolorbox} 

% Blue box with rounded corners
\tcbset{
  bluebox/.style={
    colback=blue!5,        % light blue background
    colframe=blue!70!black,% blue border
    arc=4mm,               % corner roundness
    boxrule=0.8pt,         % border thickness
    left=4pt, right=4pt, top=4pt, bottom=4pt,
    fonttitle=\bfseries,
    boxsep=4pt
  }
}

% Definition of the theorem box with a title
\newtcolorbox{theorembox}[1]{
  colback=gray!5,          % Body background (very light grey)
  colframe=gray!50,        % Border and Title background (medium grey)
  coltitle=black,          % Title text color
  fonttitle=\bfseries,     % Title font weight
  title={#1},              % The argument you pass (e.g., "Théorème 1")
  arc=3mm,                 % Rounded corners
  boxrule=0.5pt,           % Border thickness
  left=6pt, right=6pt, top=6pt, bottom=6pt,
  enhanced                 % Better drawing engine
}

% ----------- for links --------------
%\usepackage[colorlinks=true, linkcolor=blue, urlcolor=blue, citecolor=blue]{hyperref}
\usepackage[hidelinks]{hyperref}







